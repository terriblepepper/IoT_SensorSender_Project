from flask import Flask, render_template, request
from flask_socketio import SocketIO, emit
import threading
import socket
import os
import csv
import json
from datetime import datetime

app = Flask(__name__)
app.config['SECRET_KEY'] = 'secret!'
socketio = SocketIO(app)

# 初始化数据存储目录
DATA_DIR = "data"
if not os.path.exists(DATA_DIR):
    os.makedirs(DATA_DIR)  # 创建 data 文件夹


def save_to_csv(data, user_ip):
    """
    将数据保存到特定用户的 CSV 文件
    """
    filename = f"{DATA_DIR}/{user_ip}_sensor_data.csv"
    is_new_file = not os.path.exists(filename) or os.path.getsize(filename) == 0

    with open(filename, mode='a', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)
        # 写入表头（如果文件是新创建的）
        if is_new_file:
            headers = [
                "Timestamp",
                "Latitude", "Longitude",  # 经纬度在前
                "Accelerometer_x", "Accelerometer_y", "Accelerometer_z",
                "Orientation_azimuth", "Orientation_pitch", "Orientation_roll",
                "Light"
            ]
            writer.writerow(headers)

        row_data = [data["Timestamp"]]
        # Location 数据是 [latitude, longitude]
        location_data = data.get("Location", [None, None])
        row_data.extend([location_data[0], location_data[1]])  # 先纬度后经度，或者根据你的偏好

        row_data.extend(data.get("Accelerometer", [None] * 3))
        row_data.extend(data.get("Orientation", [None] * 3))
        row_data.append(data.get("Light"))  # 光线是单个值

        writer.writerow(row_data)


@app.route("/")
def index():
    """
    返回主页
    """
    return render_template("index.html")


def socket_server(host="0.0.0.0", port=8888):
    """
    用于接收传感器数据的独立线程
    """
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.bind((host, port))
        server.listen(5)
        print(f"服务器正在监听 {host}:{port} ...")

        while True:
            client_socket, client_address = server.accept()
            print(f"来自 {client_address} 的连接已建立")
            threading.Thread(target=handle_client, args=(client_socket, client_address)).start()


def handle_client(client_socket, client_address):
    """
    处理单个客户端的连接
    """
    user_ip = client_address[0]
    print(f"处理来自 {client_address} 的连接...")
    buffer = ""  # 用于处理粘包问题
    with client_socket:
        while True:
            try:
                data_chunk = client_socket.recv(1024)
                if not data_chunk:
                    print(f"[{user_ip}] 客户端关闭连接")
                    if buffer:  # 处理缓冲区中剩余的数据
                        print(f"[{user_ip}] 处理断开连接前缓冲区剩余数据: {buffer!r}")
                        process_buffered_data(buffer, user_ip)
                    break

                buffer += data_chunk.decode('utf-8', errors='replace')  # 解码并累加到缓冲区
                print(f"[{user_ip}] 接收到数据块, 当前缓冲区: {buffer!r}")

                # 尝试按换行符分割 JSON 对象
                while '\n' in buffer:
                    message, buffer = buffer.split('\n', 1)
                    if message:  # 确保消息不为空
                        print(f"[{user_ip}] 处理消息: {message!r}")
                        parsed_data = parse_data(message)
                        if parsed_data:
                            # 检查解析出的数据是否有意义 (Location, Accelerometer, Orientation, Light)
                            if any(parsed_data.get(key) is not None for key in
                                   ["Location", "Accelerometer", "Orientation", "Light"]):
                                print(f"[{user_ip}] 有效数据已解析并准备保存/发送: {parsed_data}")
                                save_to_csv(parsed_data, user_ip)
                                parsed_data['userId'] = user_ip
                                socketio.emit('update', parsed_data)
                            else:
                                print(f"[{user_ip}] 解析成功但未包含指定传感器数据.")
                        else:
                            print(f"[{user_ip}] 数据解析失败 (parse_data 返回 None) for message: {message!r}")

            except ConnectionResetError:
                print(f"[{user_ip}] 连接被客户端重置.")
                if buffer:
                    print(f"[{user_ip}] 处理重置前缓冲区剩余数据: {buffer!r}")
                    process_buffered_data(buffer, user_ip)
                break
            except UnicodeDecodeError as e:
                print(f"[{user_ip}] Unicode解码错误: {e}. 缓冲区内容: {buffer!r}")
                buffer = ""  # 清空可能有问题的缓冲区
            except Exception as e:
                print(f"[{user_ip}] 处理客户端数据时发生错误: {e}")
                if buffer:
                    print(f"[{user_ip}] 处理错误前缓冲区剩余数据: {buffer!r}")
                    process_buffered_data(buffer, user_ip)
                break
    print(f"[{user_ip}] 与客户端的连接处理结束.")


def process_buffered_data(buffer_content, user_ip):
    """辅助函数，用于处理在连接断开或发生错误前缓冲区中可能存在的完整或不完整的JSON消息"""
    messages = buffer_content.strip().split('\n')  # 按换行符分割，并去除首尾空白
    for msg in messages:
        if msg:
            print(f"[{user_ip}] 尝试处理缓冲区消息: {msg!r}")
            parsed = parse_data(msg)
            if parsed and any(
                    parsed.get(key) is not None for key in ["Location", "Accelerometer", "Orientation", "Light"]):
                save_to_csv(parsed, user_ip)
                parsed['userId'] = user_ip
                socketio.emit('update', parsed)
            else:
                print(f"[{user_ip}] 缓冲区消息解析失败或无有效数据: {msg!r}")


def parse_data(data_str):
    """
    解析接收到的 JSON 格式的传感器数据
    Android 端发送的键: "Location", "Accelerometer", "Orientation", "Light"
    """
    try:
        data_json = json.loads(data_str.strip())  # 去除可能的首尾空白
        # print(f"Parsed JSON object: {data_json}")

        result = {
            "Timestamp": datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')[:-3],
            "Location": None,
            "Accelerometer": None,
            "Orientation": None,
            "Light": None
        }

        # 直接使用 Android 端发送的键名
        if "Location" in data_json and isinstance(data_json["Location"], list) and len(data_json["Location"]) == 2:
            result["Location"] = data_json["Location"]  # [latitude, longitude]

        if "Accelerometer" in data_json and isinstance(data_json["Accelerometer"], list) and len(
                data_json["Accelerometer"]) == 3:
            result["Accelerometer"] = data_json["Accelerometer"]

        if "Orientation" in data_json and isinstance(data_json["Orientation"], list) and len(
                data_json["Orientation"]) == 3:
            result["Orientation"] = data_json["Orientation"]

        if "Light" in data_json and isinstance(data_json["Light"], (int, float)):
            result["Light"] = data_json["Light"]

        # print(f"Parsed data result: {result}")
        return result
    except json.JSONDecodeError as e:
        print(f"JSON解码错误: {e} - 原始数据: {data_str!r}")
        return None
    except Exception as e:
        print(f"解析数据时发生未知错误: {e} - 原始数据: {data_str!r}")
        return None


if __name__ == "__main__":
    # 确保 Flask-SocketIO 使用 eventlet 或 gevent，或者允许 Werkzeug 的开发服务器 (不推荐用于生产)
    # 对于开发，allow_unsafe_werkzeug=True 可以用，但 SocketIO 更推荐使用 eventlet 或 gevent
    # 例如: import eventlet; eventlet.monkey_patch(); socketio.run(app, host="0.0.0.0", port=8080)
    threading.Thread(target=socket_server, daemon=True).start()  # 设置为守护线程
    socketio.run(app, host="0.0.0.0", port=8080, allow_unsafe_werkzeug=True)