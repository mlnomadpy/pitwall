import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
import numpy as np
import time
import os

HORIZON = 20
FRAME_FEAT = 22
CONTEXT_FEAT = 8
COARSE_FEAT = 8
CORNER_EMBED_DIM = 12
MAX_CORNERS = 20
OUTPUT_FEAT = 3
HISTORY_COARSE = 5

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
print(f"Device: {DEVICE}, GPUs: {torch.cuda.device_count()}")

class TensorDataset(Dataset):
    def __init__(self, path, augment=False):
        data = np.load(path)
        self.fine = torch.tensor(data["fine"], dtype=torch.float32)
        self.med = torch.tensor(data["med"], dtype=torch.float32)
        self.coarse = torch.tensor(data["coarse"], dtype=torch.float32)
        self.ctx = torch.tensor(data["ctx"], dtype=torch.float32)
        self.corner_ids = torch.tensor(data["corner_ids"], dtype=torch.long)
        self.targets = torch.tensor(data["targets"], dtype=torch.float32)
        self.quality = torch.tensor(data["quality"], dtype=torch.float32)
        self.last_targets = torch.tensor(data["last_targets"], dtype=torch.float32)
        self.augment = augment
        print(f"  Loaded {len(self)} sequences from {path}")

    def __len__(self):
        return len(self.fine)

    def __getitem__(self, idx):
        fine = self.fine[idx]
        med = self.med[idx]
        coarse = self.coarse[idx]
        ctx = self.ctx[idx]
        cid = self.corner_ids[idx]
        tgt = self.targets[idx]
        qual = self.quality[idx]
        last = self.last_targets[idx]

        if self.augment and torch.rand(1).item() < 0.3:
            fine = fine.clone()
            med = med.clone()
            for t in [fine, med]:
                t[:, 1] *= -1
                t[:, 5] *= -1
                t[:, 8] *= -1
                t[:, 17] *= -1
            ctx = ctx.clone()
            ctx[2] *= -1

        if self.augment and torch.rand(1).item() < 0.2:
            fine = fine + torch.randn_like(fine) * 0.01
            med = med + torch.randn_like(med) * 0.01

        return fine, med, coarse, ctx, cid, tgt, qual, last


class LSTMPredictorV3(nn.Module):
    def __init__(self):
        super().__init__()
        self.corner_embed = nn.Embedding(MAX_CORNERS + 1, CORNER_EMBED_DIM)
        self.lstm_fine = nn.LSTM(FRAME_FEAT, 64, 2, batch_first=True, bidirectional=True, dropout=0.15)
        self.lstm_med = nn.LSTM(FRAME_FEAT, 48, 1, batch_first=True, bidirectional=True)
        self.coarse_encoder = nn.Sequential(nn.Linear(HISTORY_COARSE * COARSE_FEAT, 32), nn.ReLU())
        self.attention = nn.Sequential(nn.Linear(128, 48), nn.Tanh(), nn.Linear(48, 1))
        self.ctx_encoder = nn.Sequential(nn.Linear(CONTEXT_FEAT, 48), nn.ReLU(), nn.Linear(48, 48))

        decoder_in = 128 + 96 + 32 + 48 + CORNER_EMBED_DIM
        self.decoder = nn.Sequential(
            nn.Linear(decoder_in, 256), nn.ReLU(), nn.Dropout(0.15),
            nn.Linear(256, 192), nn.ReLU(), nn.Dropout(0.1),
            nn.Linear(192, HORIZON * OUTPUT_FEAT),
        )

    def forward(self, fine, med, coarse, ctx, corner_id, last_targets):
        batch = fine.shape[0]
        last_t = torch.stack([fine[:, -1, 0], fine[:, -1, 3], fine[:, -1, 4]], dim=1)

        fine_out, _ = self.lstm_fine(fine)
        attn_w = torch.softmax(self.attention(fine_out), dim=1)
        fine_vec = (fine_out * attn_w).sum(dim=1)

        med_out, _ = self.lstm_med(med)
        med_vec = med_out.mean(dim=1)

        coarse_vec = self.coarse_encoder(coarse.view(batch, -1))
        ctx_vec = self.ctx_encoder(ctx)
        corner_vec = self.corner_embed(corner_id)

        combined = torch.cat([fine_vec, med_vec, coarse_vec, ctx_vec, corner_vec], dim=1)
        deltas = self.decoder(combined).view(batch, HORIZON, OUTPUT_FEAT)

        return last_t.unsqueeze(1) + torch.cumsum(deltas, dim=1)


def train():
    print("Loading data...")
    train_ds = TensorDataset("/kaggle/working/train.npz", augment=True)
    val_ds = TensorDataset("/kaggle/working/val.npz", augment=False)
    test_ds = TensorDataset("/kaggle/working/test.npz", augment=False)

    model = LSTMPredictorV3().to(DEVICE)

    if torch.cuda.device_count() > 1:
        model = nn.DataParallel(model)
        print(f"  Using {torch.cuda.device_count()} GPUs")

    param_count = sum(p.numel() for p in model.parameters())
    print(f"  Parameters: {param_count:,}")

    optimizer = torch.optim.AdamW(model.parameters(), lr=0.001, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=150)
    huber = nn.SmoothL1Loss(reduction="none")

    train_loader = DataLoader(train_ds, batch_size=512, shuffle=True, drop_last=True, num_workers=2, pin_memory=True)
    val_loader = DataLoader(val_ds, batch_size=512, shuffle=False, num_workers=2, pin_memory=True)
    test_loader = DataLoader(test_ds, batch_size=512, shuffle=False, num_workers=2, pin_memory=True)

    hw = torch.linspace(1.5, 0.5, HORIZON).to(DEVICE)
    tw = torch.tensor([2.0, 1.5, 1.0]).to(DEVICE)

    best_val = float("inf")
    best_state = None
    patience = 25
    no_improve = 0

    print(f"\nTraining: {len(train_ds)} train, {len(val_ds)} val, {len(test_ds)} test")
    print(f"Batch: 512, Epochs: 150\n")

    t0 = time.time()
    for epoch in range(150):
        model.train()
        train_loss = 0
        for fine, med, coarse, ctx, cid, targets, quality, last_t in train_loader:
            fine, med, coarse = fine.to(DEVICE), med.to(DEVICE), coarse.to(DEVICE)
            ctx, cid = ctx.to(DEVICE), cid.to(DEVICE)
            targets, quality, last_t = targets.to(DEVICE), quality.to(DEVICE), last_t.to(DEVICE)

            pred = model(fine, med, coarse, ctx, cid, last_t)
            diff = huber(pred, targets)
            diff = diff * tw.unsqueeze(0).unsqueeze(0)
            diff = diff.mean(dim=2) * hw.unsqueeze(0)
            diff = diff.mean(dim=1) * quality
            loss = diff.mean()

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            train_loss += loss.item()

        train_loss /= len(train_loader)
        scheduler.step()

        model.eval()
        val_loss = 0
        with torch.no_grad():
            for fine, med, coarse, ctx, cid, targets, quality, last_t in val_loader:
                fine, med, coarse = fine.to(DEVICE), med.to(DEVICE), coarse.to(DEVICE)
                ctx, cid = ctx.to(DEVICE), cid.to(DEVICE)
                targets, last_t = targets.to(DEVICE), last_t.to(DEVICE)
                pred = model(fine, med, coarse, ctx, cid, last_t)
                diff = huber(pred, targets)
                diff = diff * tw.unsqueeze(0).unsqueeze(0)
                diff = diff.mean(dim=2) * hw.unsqueeze(0)
                val_loss += diff.mean().item()
        val_loss /= max(len(val_loader), 1)

        if val_loss < best_val:
            best_val = val_loss
            raw = model.module if hasattr(model, "module") else model
            best_state = {k: v.cpu().clone() for k, v in raw.state_dict().items()}
            no_improve = 0
            marker = " *"
        else:
            no_improve += 1
            marker = ""

        if epoch % 10 == 0 or marker.strip():
            elapsed = time.time() - t0
            print(f"  Epoch {epoch:>3}/150  train={train_loss:.6f}  val={val_loss:.6f}  time={elapsed:.0f}s{marker}")

        if no_improve >= patience:
            print(f"  Early stopping at epoch {epoch}")
            break

    total_time = time.time() - t0
    print(f"\nTraining complete in {total_time:.0f}s")

    # Restore best
    raw = model.module if hasattr(model, "module") else model
    raw.load_state_dict(best_state)
    raw.to(DEVICE)
    raw.eval()

    # Evaluate
    for name, loader in [("Val", val_loader), ("Test", test_loader)]:
        all_pred, all_true = [], []
        with torch.no_grad():
            for fine, med, coarse, ctx, cid, targets, quality, last_t in loader:
                fine, med, coarse = fine.to(DEVICE), med.to(DEVICE), coarse.to(DEVICE)
                ctx, cid = ctx.to(DEVICE), cid.to(DEVICE)
                last_t = last_t.to(DEVICE)
                pred = raw(fine, med, coarse, ctx, cid, last_t).cpu().numpy()
                all_pred.append(pred)
                all_true.append(targets.numpy())

        Y_pred = np.concatenate(all_pred)
        Y_true = np.concatenate(all_true)

        scales = [60.0 * 3.6, 100.0, 3.0]
        names_list = ["speed(km/h)", "brake(bar)", "throttle_r"]

        print(f"\n{name} ({Y_true.shape[0]} seq):")
        print(f"  {'':15} {'MAE':>8} {'0.5s':>8} {'1.0s':>8} {'1.5s':>8} {'2.0s':>8}")
        for t_idx in range(3):
            true = Y_true[:, :, t_idx] * scales[t_idx]
            pred_s = Y_pred[:, :, t_idx] * scales[t_idx]
            mae = np.mean(np.abs(true - pred_s))
            m05 = np.mean(np.abs(true[:, 4] - pred_s[:, 4]))
            m10 = np.mean(np.abs(true[:, 9] - pred_s[:, 9]))
            m15 = np.mean(np.abs(true[:, 14] - pred_s[:, 14]))
            m20 = np.mean(np.abs(true[:, 19] - pred_s[:, 19]))
            print(f"  {names_list[t_idx]:15} {mae:>8.2f} {m05:>8.2f} {m10:>8.2f} {m15:>8.2f} {m20:>8.2f}")

        sd = (Y_pred[:, 9, 0] - Y_true[:, 9, 0]) * 60.0 * 3.6
        print(f"  Speed bias @1s: {np.mean(sd):+.1f} +/- {np.std(sd):.1f} km/h")

    torch.save({"model_state": best_state}, "/kaggle/working/lstm_v3_gpu.pt")
    print(f"\nModel saved: {os.path.getsize('/kaggle/working/lstm_v3_gpu.pt')/1024:.0f} KB")


if __name__ == "__main__":
    train()
