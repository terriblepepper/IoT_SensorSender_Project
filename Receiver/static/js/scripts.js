// 初始化全局变量
const socket = io();
let isPaused = false; // 实时更新开关
const dataHistory = []; // 历史数据存储

// 初始化图表
const initChart = (ctx, labels, datasets) => {
    return new Chart(ctx, {
        type: "line",
        data: {
            labels: [],
            datasets: datasets.map((dataset) => ({
                label: dataset.label,
                data: [],
                borderColor: dataset.color,
                fill: false
            }))
        },
        options: { responsive: true }
    });
};

// 配置光强图表
const lightChart = initChart(
    document.getElementById("lightChart"),
    [],
    [{ label: "光强 (lx)", color: "rgb(255, 99, 132)" }]
);

// 配置加速度图表
const accelerometerChart = initChart(
    document.getElementById("accelerometerChart"),
    [],
    [
        { label: "X轴加速度", color: "rgb(75, 192, 192)" },
        { label: "Y轴加速度", color: "rgb(54, 162, 235)" },
        { label: "Z轴加速度", color: "rgb(153, 102, 255)" }
    ]
);

// 配置陀螺仪图表
const gyroscopeChart = initChart(
    document.getElementById("gyroscopeChart"),
    [],
    [
        { label: "X轴角速度", color: "rgb(255, 206, 86)" },
        { label: "Y轴角速度", color: "rgb(255, 159, 64)" },
        { label: "Z轴角速度", color: "rgb(201, 203, 207)" }
    ]
);

// 地图初始化
const map = L.map("map").setView([30.0, 120.0], 10);
L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png").addTo(map);
const pathLayer = L.polyline([], { color: "blue" }).addTo(map);
const geoFence = L.circle([30.0, 120.0], { radius: 1000, color: "red" }).addTo(map);

// 实时数据更新逻辑
socket.on("update", (data) => {
    if (isPaused) return;

    const timestamp = data.Timestamp || new Date().toLocaleTimeString();

    // 更新光强图表
    if (data.Light) {
        lightChart.data.labels.push(timestamp);
        lightChart.data.datasets[0].data.push(data.Light);
        lightChart.update();
    }

    // 更新加速度图表
    if (data.Accelerometer) {
        accelerometerChart.data.labels.push(timestamp);
        accelerometerChart.data.datasets.forEach((dataset, i) => {
            dataset.data.push(data.Accelerometer[i]);
        });
        accelerometerChart.update();
    }

    // 更新陀螺仪图表
    if (data.Gyroscope) {
        gyroscopeChart.data.labels.push(timestamp);
        gyroscopeChart.data.datasets.forEach((dataset, i) => {
            dataset.data.push(data.Gyroscope[i]);
        });
        gyroscopeChart.update();
    }

    // 更新地图
    if (data.Location) {
        const [longitude, latitude] = data.Location;
        const latLng = [latitude, longitude];
        pathLayer.addLatLng(latLng);
        map.setView(latLng, map.getZoom());
    }

    // 保存历史数据
    dataHistory.push(data);
});

// 暂停/恢复实时更新按钮逻辑
document.getElementById("pauseResumeBtn").addEventListener("click", () => {
    isPaused = !isPaused;
    document.getElementById("pauseResumeBtn").innerText = isPaused ? "恢复实时更新" : "暂停实时更新";
});

// 导出数据为 CSV
document.getElementById("exportDataBtn").addEventListener("click", () => {
    const csvContent =
        "data:text/csv;charset=utf-8," +
        ["时间戳,光强,加速度X,加速度Y,加速度Z,角速度X,角速度Y,角速度Z,经度,纬度"]
            .concat(
                dataHistory.map((d) =>
                    [
                        d.Timestamp || "",
                        d.Light || "",
                        ...(d.Accelerometer || ["", "", ""]),
                        ...(d.Gyroscope || ["", "", ""]),
                        ...(d.Location || ["", ""])
                    ].join(",")
                )
            )
            .join("\n");

    const link = document.createElement("a");
    link.href = encodeURI(csvContent);
    link.download = "sensor_data.csv";
    link.click();
});