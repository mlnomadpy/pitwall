Gemma on-device (MediaPipe LLM Inference)
========================================

Place the LiteRT task bundle here:

  Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task

Filename must match exactly. On first launch it is copied into app internal storage.

Download (same artifact as Google’s mediapipe-samples llm_inference Android app):
  https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task

Docs:
  https://ai.google.dev/gemma/docs/integrations/mobile

If this file is missing, the bridge still runs but coach/score/brief calls use deterministic stub text until you add the model.
