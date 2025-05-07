from flask import Flask, render_template, request
from flask_socketio import SocketIO, emit
import threading
import socket
import os
import csv
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
    with open(filename, mode='a', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)
        # 写入表头（如果文件是新创建的）
        if file.tell() == 0:
            writer.writerow([
                "Timestamp", "Gyroscope_x", "Gyroscope_y", "Gyroscope_z",
                "Accelerometer_x", "Accelerometer_y", "Accelerometer_z",
                "Longitude", "Latitude", "Light", "Magnetometer_x", "Magnetometer_y", "Magnetometer_z"
            ])
        writer.writerow([
            data["Timestamp"],
            *data.get("Gyroscope", [None, None, None]),
            *data.get("Accelerometer", [None, None, None]),
            *data.get("Location", [None, None]),
            data.get("Light"),
            *data.get("Magnetometer", [None, None, None])
        ])


@app.route("/")
def index():
    """
    返回主页
    """
    return render_template("index.html")


def socket_server(host="0.0.0.0", port=12345):
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
    user_ip = client_address[0]  # 获取客户端 IP 地址
    with client_socket:
        while True:
            data = client_socket.recv(1024)
            if not data:
                break
            data_decoded = data.decode('utf-8')
            parsed_data = parse_data(data_decoded)

            if parsed_data:
                save_to_csv(parsed_data, user_ip)
                parsed_data['userId'] = user_ip  # 将 IP 地址作为用户标识传递
                socketio.emit('update', parsed_data)


def parse_data(data):
    """
    解析接收到的传感器数据
    """
    try:
        result = {
            "Timestamp": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            "Gyroscope": None,
            "Accelerometer": None,
            "Location": None,
            "Light": None,
            "Magnetometer": None
        }
        if "陀螺仪传感器" in data:
            gyro = data.split("陀螺仪传感器=")[1].split(",加速度传感器=")[0]
            result["Gyroscope"] = [float(value.split('=')[1]) for value in gyro.split(",")]

        if "加速度传感器" in data:
            accel = data.split("加速度传感器=")[1].split(",经纬度=")[0]
            result["Accelerometer"] = [float(value.split('=')[1]) for value in accel.split(",")]

        if "经纬度" in data:
            location = data.split("经纬度=")[1].split(",光线传感器=")[0]
            latitude = float(location.split("纬度=")[1].split("°")[0])
            longitude = float(location.split("经度=")[1].split("°")[0])
            result["Location"] = [longitude, latitude]

        if "光线传感器" in data:
            light = data.split("光线传感器=")[1].split(",磁场传感器=")[0]
            result["Light"] = float(light.split("光强=")[1].split(" lx")[0])

        if "磁场传感器" in data:
            magnet = data.split("磁场传感器=")[1].strip().rstrip(",")
            result["Magnetometer"] = [float(value.split('=')[1]) for value in magnet.split(",")]

        return result
    except Exception as e:
        print(f"解析数据时出错: {e}")
        return None


if __name__ == "__main__":
    threading.Thread(target=socket_server).start()
    socketio.run(app, host="0.0.0.0", port=8080)