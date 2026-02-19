import os
import json
import time
import uuid
import random
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


FINCA_ID = env("FINCA_ID", "finca1")
ZONA_ID = env("ZONA_ID", "zona1")
DEVICE_ID = env("DEVICE_ID", "pi-gw-001")

MQTT_HOST = env("MQTT_HOST", "mosquitto")
MQTT_PORT = int(env("MQTT_PORT", "1883"))
MQTT_USER = os.getenv("MQTT_USER", "")
MQTT_PASS = os.getenv("MQTT_PASS", "")

INTERVAL_SEC = int(env("INTERVAL_SEC", "60"))
SENSORS = int(env("SENSORS", "10"))

def normalize_sensor_type(raw: str) -> str:
    # "soil_moisture" -> "SOIL_MOISTURE"
    # "soil-moisture" -> "SOIL_MOISTURE"
    # "soil moisture" -> "SOIL_MOISTURE"
    return str(raw).strip().upper().replace("-", "_").replace(" ", "_")

"""
def topic_telemetry(sensor_type: str) -> str:
    return f"agro/{FINCA_ID}/{ZONA_ID}/{DEVICE_ID}/sensor/{sensor_type}/telemetry"
"""

def topic_telemetry(sensor_id: str, sensor_type: str) -> str:
    sensor_type_canon = normalize_sensor_type(sensor_type)
    return f"agro/{FINCA_ID}/{ZONA_ID}/{DEVICE_ID}/sensor/{sensor_id}/{sensor_type_canon}/telemetry"


def topic_status(sensor_id: str) -> str:
    return f"agro/{FINCA_ID}/{ZONA_ID}/{DEVICE_ID}/sensor/{sensor_id}/status"


def make_sensors(n: int):
    # 10 sensores típicos para demo (puedes ajustar luego)
    base = [
        ("soil_moisture", "%VWC"),
        ("temperature", "C"),
        ("humidity", "%RH"),
        ("ec", "mS/cm"),
        ("ph", "pH"),
        ("light", "lux"),
        ("pressure", "hPa"),
        ("wind", "m/s"),
        ("rain", "mm"),
        ("battery", "V"),
    ]
    sensors = []
    for i in range(n):
        t, unit = base[i % len(base)]
        sensor_id = f"{t}-{i+1:02d}"
        sensors.append((sensor_id, t, unit))
    return sensors


SENSOR_LIST = make_sensors(SENSORS)


def build_payload(sensor_id: str, sensor_type: str, unit: str):
    # Valores “creíbles” por tipo (rango simple para demo)
    if sensor_type == "soil_moisture":
        value = round(random.uniform(10, 45), 1)
    elif sensor_type == "temperature":
        value = round(random.uniform(5, 38), 1)
    elif sensor_type == "humidity":
        value = round(random.uniform(20, 95), 1)
    elif sensor_type == "ec":
        value = round(random.uniform(0.2, 3.5), 2)
    elif sensor_type == "ph":
        value = round(random.uniform(5.5, 8.2), 2)
    elif sensor_type == "light":
        value = int(random.uniform(50, 90000))
    elif sensor_type == "pressure":
        value = round(random.uniform(980, 1040), 1)
    elif sensor_type == "wind":
        value = round(random.uniform(0, 12), 1)
    elif sensor_type == "rain":
        value = round(random.uniform(0, 10), 1)
    elif sensor_type == "battery":
        value = round(random.uniform(3.6, 4.2), 2)
    else:
        value = round(random.uniform(0, 100), 2)

    payload = {
        "deviceId": DEVICE_ID,
        "sensorId": sensor_id,
        "type": sensor_type,
        "ts": now_iso(),
        "value": value,
        "unit": unit,
        "battery": round(random.uniform(3.6, 4.2), 2),
        "quality": "good",
        "messageId": str(uuid.uuid4()),
    }
    return payload


def main():
    client_id = f"sim-{DEVICE_ID}"
    client = mqtt.Client(client_id=client_id, clean_session=True)

    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASS)

    # LWT: si el simulador muere, marcamos offline (retenido)
    # Ojo: el will es 1 por cliente. Para demo, marcamos el gateway como “offline”.
    # Para status por sensor, lo haremos en Node-RED (agregando) o en el backend más tarde.
    will_topic = f"agro/{FINCA_ID}/{ZONA_ID}/{DEVICE_ID}/status"
    client.will_set(will_topic, payload="offline", qos=1, retain=True)

    client.connect(MQTT_HOST, MQTT_PORT, keepalive=30)

    client.loop_start()

    # Al arrancar: online retained
    client.publish(will_topic, payload="online", qos=1, retain=True)

    print(f"[sim] connected to {MQTT_HOST}:{MQTT_PORT} as {client_id}")
    print(f"[sim] publishing {len(SENSOR_LIST)} sensors every {INTERVAL_SEC}s")

    try:
        while True:
            for sensor_id, sensor_type, unit in SENSOR_LIST:
                payload = build_payload(sensor_id, sensor_type, unit)
                t = topic_telemetry(sensor_id, sensor_type)
                client.publish(t, json.dumps(payload), qos=1, retain=False)
            time.sleep(INTERVAL_SEC)
    except KeyboardInterrupt:
        print("[sim] stopping...")
    finally:
        # Publica offline “limpio” si se cierra bien (no LWT)
        client.publish(will_topic, payload="offline", qos=1, retain=True)
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
