# GStreamer WebRTCBin Example

## Prerequisites
- GStreamer 1.16 (including plugins)
- Docker
- Docker-Compose
- Chromium-Browser 76.0.3809.132

## Usage
```shell script
docker-compose up -d
./gradlew build
chromium http://localhost:80
java -jar build/libs/gstwebrtc.jar
```

## Network Simulation

For bandwidth control, https://github.com/magnific0/wondershaper is recommended.

```shell script
git clone https://github.com/magnific0/wondershaper.git
cd wondershaper
sudo ./wondershaper -a lo -u 1000 #limit upload of loopback device to 1000 kbit/s
sudo ./wondershaper -a lo -c #reset loopback device
```


**Package loss**
```shell script
sudo tc qdisc add dev lo root handle 1:0 netem loss 2% #set loss of 2% for loopback device
sudo tc qdisc del dev lo root #reset loopback device
```