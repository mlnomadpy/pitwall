"""
Standalone VBO parser for the Pitwall simulator clients.
Does not import from the backend `pitwall.features` namespace.
"""

import math
from dataclasses import dataclass
from pathlib import Path


@dataclass
class ClientTelemetryFrame:
    timestamp: float
    lat: float
    lon: float
    speed: float
    heading: float
    altitude: float
    g_lat: float
    g_long: float
    combo_g: float
    brake_pressure: float
    brake_position: float
    throttle: float
    steering: float
    rpm: float
    coolant_temp: float
    oil_temp: float
    fuel_level: float
    distance: float = 0.0

    def to_dict(self):
        return {
            "timestamp": self.timestamp,
            "lat": self.lat,
            "lon": self.lon,
            "speed": self.speed,
            "heading": self.heading,
            "altitude": self.altitude,
            "g_lat": self.g_lat,
            "g_long": self.g_long,
            "combo_g": self.combo_g,
            "brake_pressure": self.brake_pressure,
            "brake_position": self.brake_position,
            "throttle": self.throttle,
            "steering": self.steering,
            "rpm": self.rpm,
            "coolant_temp": self.coolant_temp,
            "oil_temp": self.oil_temp,
            "fuel_level": self.fuel_level,
            "distance": self.distance,
        }


def _parse_vbo_coord(value: float) -> float:
    degrees = int(value / 100)
    minutes = value - (degrees * 100)
    return degrees + minutes / 60.0


def parse_vbo_client(filepath: str) -> list[ClientTelemetryFrame]:
    filepath = Path(filepath)
    lines = filepath.read_text(encoding="utf-8", errors="ignore").splitlines()

    sections = {}
    for i, line in enumerate(lines):
        if line.strip().startswith("[") and line.strip().endswith("]"):
            sections[line.strip().strip("[]")] = i

    columns = []
    if "column names" in sections:
        columns = lines[sections["column names"] + 1].strip().split()

    if "data" not in sections or not columns:
        return []

    data_start = sections["data"] + 1
    frames = []
    cumulative_distance = 0.0
    prev_lat = None
    prev_lon = None

    for line in lines[data_start:]:
        line = line.strip()
        if not line or line.startswith("["):
            break

        parts = line.split()
        if len(parts) != len(columns):
            continue

        row = {}
        for j, col in enumerate(columns):
            try:
                row[col] = float(parts[j])
            except ValueError:
                row[col] = 0.0

        lat = _parse_vbo_coord(abs(row.get("lat", 0)))
        lon = -_parse_vbo_coord(abs(row.get("long", 0)))

        if prev_lat is not None:
            dlat = math.radians(lat - prev_lat)
            dlon = math.radians(lon - prev_lon)
            a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(prev_lat)) * math.cos(math.radians(lat)) * math.sin(dlon / 2) ** 2
            c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
            cumulative_distance += 6371000 * c

        prev_lat = lat
        prev_lon = lon

        frames.append(ClientTelemetryFrame(
            timestamp=row.get("time", 0),
            lat=lat,
            lon=lon,
            speed=row.get("velocity", 0) / 3.6,
            heading=row.get("heading", 0),
            altitude=row.get("height", 0),
            g_lat=row.get("Indicated_Lateral_Acceleration", 0),
            g_long=row.get("Indicated_Longitudinal_Acceleration", 0),
            combo_g=row.get("ComboAcc", 0),
            brake_pressure=row.get("Brake_Pressure", 0),
            brake_position=row.get("Brake_Position", 0),
            throttle=row.get("Throttle_Position", 0),
            steering=row.get("Steering_Angle", 0),
            rpm=row.get("Engine_Speed", 0),
            coolant_temp=row.get("Coolant_Temperature", 0),
            oil_temp=row.get("Oil_Temperature", 0),
            fuel_level=row.get("Fuel_Level", 0),
            distance=cumulative_distance,
        ))

    return frames
