# Phần mềm Data Diode một chiều sử dụng Python

Dự án này cung cấp một bộ phần mềm hoàn chỉnh để triển khai một Data Diode một chiều, sử dụng hai máy tính Linux (Proxy) để đảm bảo dữ liệu chỉ có thể đi từ mạng kém an toàn (Low-Side) sang mạng an toàn hơn (High-Side).

---
## 🚀 Kiến trúc hệ thống

Hệ thống hoạt động dựa trên mô hình Proxy Gửi/Nhận. Dữ liệu từ Client được các dịch vụ trên máy TX Proxy tiếp nhận, sau đó được đóng gói thành một luồng UDP một chiều hiệu quả, gửi qua Diode vật lý, và cuối cùng được máy RX Proxy tái tạo lại.

```
Mạng Low-Side (Kém an toàn)                      Mạng High-Side (An toàn)
+----------------+      +------------------+      +-----------------+      +------------------+      +----------------+
|                |      |                  |      |                 |      |                  |      |                |
|  Client        +----->|   TX Proxy       +----->| Hardware Diode  +----->|   RX Proxy       +----->|  Server Đích   |
| (SFTP, FTPS...)|      | (inet-send.py)   |      |   (One-Way)     |      | (inet_receive.py)|      | (Lưu trữ)      |
|                |      |                  |      |                 |      |                  |      |                |
+----------------+      +------------------+      +-----------------+      +------------------+      +----------------+
```

---
## 🛡️ Tính năng chính

* **Truyền dữ liệu một chiều:** Đảm bảo an toàn tuyệt đối cho mạng OT/mạng an toàn.
* **Hỗ trợ đa dịch vụ:** Dễ dàng tiếp nhận file từ Client qua các giao thức phổ biến như **SFTP**, **FTPS** bằng cách cấu hình các dịch vụ tương ứng trên máy TX Proxy.
* **Hiệu năng cao:** Sử dụng giao thức nhị phân (binary) tối ưu trên nền UDP và kiến trúc đa tiến trình (multiprocessing) để đạt tốc độ truyền cao, có thể lên đến ~800Mbps trên mạng 1Gbps.
* **Đảm bảo toàn vẹn dữ liệu:** Sử dụng cơ chế kiểm tra MD5 cho toàn bộ file để xác nhận file ở bên nhận là một bản sao hoàn hảo của file gốc.
* **Độ tin cậy cao:** Tự động gửi lại các gói tin điều khiển quan trọng (`START`/`END`) để chống mất gói tin trên đường truyền UDP.
* **Quản lý dễ dàng:** Toàn bộ hệ thống được quản lý bởi các dịch vụ `systemd`, tự động khởi động và phục hồi khi có lỗi.

---
## ⚙️ Yêu cầu Hệ thống

* **Phần cứng:** 2 máy tính chạy Ubuntu Server (một làm TX Proxy, một làm RX Proxy), và một thiết bị Data Diode vật lý (hoặc dây cáp mạng nối thẳng để kiểm tra).
* **Phần mềm:**
    * Python 3.8+ và pip.
    * `ethtool` để tinh chỉnh mạng.
    * `iperf3` để kiểm tra tốc độ mạng.

---
##  kurulum Cài đặt và Cấu hình

### 1. Chuẩn bị chung

Clone dự án này về cả hai máy TX và RX.

```bash
git clone https://github.com/bmctechvn/BMC-iNET-11A.git
cd BMC-iNET-11A
```

Trên cả hai máy, cài đặt các thư viện Python cần thiết:
```bash
sudo apt update
sudo pip3 install -r requirements.txt
```
### 2. Cấu hình Máy TX Proxy (Bên Gửi)

Máy TX Proxy là nơi tiếp nhận file từ người dùng.

#### a. Cài đặt các dịch vụ đầu vào
Tùy theo nhu cầu, bạn có thể cài đặt SFTP (OpenSSH), FTPS (vsftpd), v.v. Chi tiết về cách cài đặt và "nhốt" (jail) user đã được thảo luận. Thư mục upload của user nên được cấu hình để trỏ đến thư mục nguồn mà `inet_send` sẽ theo dõi.

#### b. Cấu hình Script `inet_send`
Script này được chạy thông qua các tham số dòng lệnh. Bạn sẽ cấu hình chúng trong file service.
* `--directory`: Thư mục nguồn để quét tìm file gửi đi (ví dụ: `/home/data_source/`).
* `--target-subnet`: Địa chỉ IP của máy RX Proxy (ví dụ: `10.10.2.3`).
* `--target-port`: Port của máy RX Proxy (ví dụ: `9009`).

#### c. Tạo dịch vụ `systemd` cho `inet-send.py`

Tạo file `/etc/systemd/system/diode-send.service`:
```bash
sudo nano /etc/systemd/system/diode-send.service
```

Dán nội dung sau và tùy chỉnh lại các đường dẫn và tham số:
```ini
[Unit]
Description=Data Diode Sender Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root # Hoặc user có quyền đọc thư mục nguồn và chạy script
ExecStart=/usr/bin/python3 /path/to/your/project/inet_send --directory /path/to/source_dir --target-subnet 10.10.2.3 --target-port 9009
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
```

### 3. Cấu hình Máy RX Proxy (Bên Nhận)

Máy RX Proxy nhận dữ liệu từ Diode và di chuyển vào thư mục cuối cùng.

#### a. Cấu hình Script `inet_receive`
Tương tự, script này cũng nhận tham số qua dòng lệnh.
* `--directory`: Thư mục tạm để tái tạo file (ví dụ: `/var/data/receiving/`).
* `--server-directory`: Thư mục cuối cùng để di chuyển file đã nhận thành công (ví dụ: `/home/bmc/done/`).
* `--bind-subnet`: Địa chỉ IP của chính máy RX Proxy (ví dụ: `10.10.2.3`).
* `--bind-port`: Port để lắng nghe (ví dụ: `9009`).

#### b. Tạo dịch vụ `systemd` cho `inet_receive`

Tạo file `/etc/systemd/system/diode-receive.service`:
```bash
sudo nano /etc/systemd/system/diode-receive.service
```

Dán nội dung sau và tùy chỉnh:
```ini
[Unit]
Description=Data Diode Receiver Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root # Hoặc user có quyền ghi vào các thư mục đích
ExecStart=/usr/bin/python3 /path/to/your/project/inet_receive.py --directory /var/data/receiving --server-directory /home/bmc/done --bind-subnet 10.10.2.3 --bind-port 9009
Restart=on-failure
RestartSec=15

[Install]
WantedBy=multi-user.target
```

---
## ▶️ Vận hành Dịch vụ

Sau khi đã tạo các file service trên cả hai máy, hãy chạy các lệnh sau:

```bash
# Tải lại cấu hình systemd
sudo systemctl daemon-reload

# Kích hoạt dịch vụ để tự khởi động cùng hệ thống
sudo systemctl enable diode-send.service # (Trên máy TX)
sudo systemctl enable diode-receive.service # (Trên máy RX)

# Khởi động dịch vụ ngay lập tức
sudo systemctl start diode-send.service # (Trên máy TX)
sudo systemctl start diode-receive.service # (Trên máy RX)

# Kiểm tra trạng thái
sudo systemctl status diode-send.service
sudo systemctl status diode-receive.service

# Xem log trực tiếp
sudo journalctl -u diode-send.service -f
sudo journalctl -u diode-receive.service -f
```

---
## ⚡ Tối ưu Hiệu năng

Để đạt được tốc độ cao (~800Mbps), hãy đảm bảo bạn đã thực hiện các bước tối ưu sau:

1.  **Kích hoạt Jumbo Frames:** Tăng MTU của card mạng trên cả hai máy lên 9000.
    ```bash
    # Lệnh tạm thời
    sudo ip link set <tên_card_mạng> mtu 9000
    # Cấu hình vĩnh viễn trong Netplan
    sudo nano /etc/netplan/50-cloud-init.yaml
    network:
        version: 2
        ethernets:
            enp1s0:
                dhcp4: true
            enp2s0:
                dhcp4: no
                addresses:
                    - 10.10.2.3/24
                mtu: 9000
    ```
    Đảm bảo `CHUNK_SIZE` trong `inet_send` được đặt khoảng `8900`.

2.  **Tăng bộ đệm UDP (Trên máy RX):**
    ```bash
    sudo sysctl -w net.core.rmem_max=26214400
    sudo sysctl -w net.core.rmem_default=26214400
    ```
    Thêm vào `/etc/sysctl.conf` để có hiệu lực vĩnh viễn.
        net.core.rmem_max=26214400
        net.core.rmem_default=26214400
3.  **Tinh chỉnh `DELAY_NEXT_CHUNK` (Trên máy TX):**
    Trong file `inet_send`, giảm dần giá trị `DELAY_NEXT_CHUNK` để tìm ra tốc độ cao nhất mà hệ thống vẫn chạy ổn định.


### Cài đặt các Gói Hệ thống (APT Dependencies)

Trước khi chạy các script Python, bạn cần đảm bảo các gói phần mềm hệ thống cần thiết đã được cài đặt thông qua `apt`.

#### **Trên cả hai máy (TX và RX Proxy):**
Các công cụ này cần thiết cho việc cài đặt thư viện Python, kiểm tra và tối ưu hiệu năng mạng.
```bash
sudo apt update
sudo apt install -y python3-pip ethtool iperf3
```

#### **Trên máy TX Proxy (Bên Gửi):**
Cài đặt các dịch vụ server để tiếp nhận file từ người dùng cuối.
```bash
# Cài đặt dịch vụ SFTP (tích hợp sẵn) và FTPS
sudo apt install -y openssh-server vsftpd
```
*Lưu ý: Nếu bạn cần hỗ trợ chia sẻ file cho máy Windows (giao thức SMB/CIFS), hãy cài đặt thêm `samba`.*
```bash
# sudo apt install -y samba
```

#### **Trên máy RX Proxy (đến Server cuối):**
Cài đặt `rsync` để script `sync_and_clear.py` có thể đồng bộ hóa dữ liệu.
```bash
sudo apt install -y rsync
```
*Lưu ý: `sshpass` chỉ cần thiết nếu bạn bắt buộc phải dùng mật khẩu thay cho SSH key. Việc này không được khuyến nghị vì lý do bảo mật.*
```bash
# CẢNH BÁO: CHỈ CÀI ĐẶT KHI CẦN THIẾT
# sudo apt install -y sshpass
```
## 5. 🔑 Cấu hình Xác thực không mật khẩu (SSH Key) cho Rsync

Để script tự động đẩy dữ liệu từ **RX Proxy** sang **Server cuối** có thể hoạt động mà không cần can thiệp thủ công, bạn phải thiết lập xác thực bằng SSH key.

Quy trình này bao gồm việc tạo một cặp key trên máy RX Proxy và cấp phép cho public key trên Server cuối.

### 5.1. Trên máy RX Proxy (Nơi chạy script gửi)

Bước này tạo ra một cặp khóa an toàn.

1.  **Chạy lệnh `ssh-keygen`:**
    Tạo một cặp key RSA 4096-bit mới. Đặt tên file key một cách gợi nhớ, ví dụ `rx_to_server_key`.

    ```bash
    ssh-keygen -t rsa -b 4096 -f ~/.ssh/rx_to_server_key -C "RX Proxy to Final Server rsync key"
    ```

2.  **Để trống Passphrase:**
    Khi được hỏi `Enter passphrase (empty for no passphrase):`, hãy **nhấn Enter hai lần** để bỏ qua. Điều này là **bắt buộc** để script có thể sử dụng key mà không cần hỏi mật khẩu.

Sau bước này, bạn sẽ có 2 file mới trong thư mục `~/.ssh/`:
* `rx_to_server_key`: **Private Key** 🔒 (Khóa riêng tư, tuyệt đối bí mật).
* `rx_to_server_key.pub`: **Public Key** 🌍 (Khóa công khai, dùng để chia sẻ).

### 5.2. Trên Server Cuối (Nơi nhận dữ liệu)

Bước này cấp phép cho Public Key của máy RX được phép kết nối.

#### **Cách 1: Dùng `ssh-copy-id` (Khuyến nghị)**

Đây là cách dễ dàng và an toàn nhất. Từ máy **RX Proxy**, chạy lệnh sau:

```bash
# Thay user và <IP_SERVER_CUOI> bằng thông tin của bạn
ssh-copy-id -i ~/.ssh/rx_to_server_key.pub user@<IP_SERVER_CUOI>
```
Bạn sẽ được yêu cầu nhập mật khẩu của `user` trên Server cuối **một lần duy nhất**. Lệnh này sẽ tự động sao chép key và thiết lập quyền truy cập đúng.

#### **Cách 2: Sao chép thủ công**

Nếu không có `ssh-copy-id`, hãy làm thủ công:

1.  **Trên máy RX Proxy**, lấy nội dung public key:
    ```bash
    cat ~/.ssh/rx_to_server_key.pub
    ```
    Sao chép toàn bộ chuỗi `ssh-rsa...` hiển thị ra.

2.  **Đăng nhập vào Server cuối**.

3.  Dán public key đã sao chép vào một dòng mới trong file `~/.ssh/authorized_keys`.
    ```bash
    # Tạo file và thư mục nếu chưa có
    mkdir -p ~/.ssh
    touch ~/.ssh/authorized_keys
    
    # Mở file và dán key vào
    nano ~/.ssh/authorized_keys
    ```

4.  **Thiết lập quyền truy cập (Rất quan trọng):**
    ```bash
    chmod 700 ~/.ssh
    chmod 600 ~/.ssh/authorized_keys
    ```

### 5.3. Cập nhật Cấu hình và Kiểm tra

1.  **Cập nhật file cấu hình:**
    Trên máy **RX Proxy**, mở file cấu hình (`uploader.ini` hoặc `.conf`) của script đẩy file và đảm bảo nó trỏ đến private key mới và đã xóa/vô hiệu hóa dòng mật khẩu.

    ```ini
    [SFTP]
    User = user_remote
    Host = <IP_SERVER_CUOI>
    Ssh_Key_Path = /home/bmc/.ssh/rx_to_server_key
    # Password = ...  <-- Đảm bảo dòng này đã bị xóa hoặc vô hiệu hóa
    ```

2.  **Kiểm tra kết nối:**
    Từ máy **RX Proxy**, chạy lệnh sau để kiểm tra:
    ```bash
    ssh -i ~/.ssh/rx_to_server_key user@<IP_SERVER_CUOI> "echo 'Kết nối SSH Key thành công!'"
    ```
    Nếu bạn thấy thông báo "Kết nối SSH Key thành công!" mà **không cần nhập mật khẩu**, nghĩa là bạn đã thiết lập thành công. Dịch vụ `rsync` của bạn giờ đã sẵn sàng để hoạt động tự động.

## 6. 윈도우 Cấu hình Chia sẻ File cho Windows (Samba)

Để cho phép người dùng từ máy tính Windows dễ dàng gửi file vào Data Diode, bạn có thể thiết lập một thư mục chia sẻ trên máy **TX Proxy** bằng dịch vụ Samba.

### 6.1. Cài đặt Samba và Cấu hình Tường lửa

1.  **Cài đặt Samba:**
    ```bash
    sudo apt update
    sudo apt install -y samba
    ```

2.  **Cho phép Samba qua tường lửa UFW:**
    ```bash
    sudo ufw allow 'Samba'
    sudo ufw reload
    ```

### 6.2. Tạo User và Thư mục Chia sẻ

1.  **Tạo thư mục chia sẻ:**
    Chúng ta sẽ dùng một thư mục riêng để đảm bảo an toàn.
    ```bash
    sudo mkdir -p /srv/samba/diode_upload
    ```

2.  **Tạo người dùng Linux:**
    Tạo một user mới (`smbuser`) và không cấp cho họ quyền đăng nhập shell để tăng bảo mật.
    ```bash
    sudo adduser --no-create-home --shell /usr/sbin/nologin smbuser
    ```

3.  **Cấp quyền thư mục:**
    ```bash
    sudo chown smbuser:smbuser /srv/samba/diode_upload
    sudo chmod 755 /srv/samba/diode_upload
    ```

4.  **Đặt mật khẩu Samba (Quan trọng):**
    Người dùng sẽ sử dụng mật khẩu này để truy cập thư mục chia sẻ.
    ```bash
    sudo smbpasswd -a smbuser
    ```
    Hệ thống sẽ yêu cầu bạn nhập và xác nhận mật khẩu mới.

### 6.3. Chỉnh sửa File Cấu hình Samba

1.  Mở file `/etc/samba/smb.conf`:
    ```bash
    sudo nano /etc/samba/smb.conf
    ```

2.  Thêm khối cấu hình sau vào cuối file:
    ```ini
    [diode_upload]
       comment = Data Diode Upload Share
       path = /srv/samba/diode_upload
       browsable = yes
       guest ok = no
       read only = no
       writable = yes
       valid users = smbuser
    ```

### 6.4. Tích hợp với Script và Khởi động lại

1.  **Cập nhật Dịch vụ `diode-send`:**
    Đảm bảo script `inet-send.py` đang theo dõi đúng thư mục mà Samba chia sẻ. Sửa file `/etc/systemd/system/diode-send.service` và cập nhật tham số `--directory`:

    ```ini
    [Service]
    ...
    ExecStart=/usr/bin/python3 /path/to/your/project/inet-send.py --directory /srv/samba/diode_upload --target-subnet ...
    ...
    ```

2.  **Khởi động lại các dịch vụ:**
    ```bash
    # Tải lại cấu hình systemd nếu bạn vừa sửa file service
    sudo systemctl daemon-reload

    # Khởi động lại Samba
    sudo systemctl restart smbd nmbd

    # Khởi động lại dịch vụ gửi file của bạn
    sudo systemctl restart diode-send.service
    ```

### 6.5. Kiểm tra Kết nối từ Windows

1.  Mở **File Explorer** trên máy Windows.
2.  Trên thanh địa chỉ, gõ `\\<IP_CUA_MAY_TX_PROXY>\diode_upload` (ví dụ: `\\10.10.2.2\diode_upload`).
3.  Khi được hỏi, nhập tên người dùng là `smbuser` và mật khẩu bạn đã tạo.
4.  Kéo-thả một file vào thư mục. File đó sẽ được script `inet-send.py` tự động phát hiện và gửi qua Data Diode.

## 7. 🐧 Cấu hình Chia sẻ File cho Linux/Unix (NFS)

Để cho phép các máy chủ Linux/Unix khác trong mạng nội bộ gửi file vào Data Diode, bạn có thể thiết lập một thư mục chia sẻ NFS (Network File System) trên máy **TX Proxy**.

### 7.1. Cài đặt NFS Server

1.  **Cài đặt gói cần thiết:**
    ```bash
    sudo apt update
    sudo apt install -y nfs-kernel-server
    ```

2.  **Tạo thư mục chia sẻ:**
    ```bash
    sudo mkdir -p /srv/nfs/diode_upload
    ```

3.  **Cấp quyền cơ bản cho thư mục:**
    ```bash
    sudo chown nobody:nogroup /srv/nfs/diode_upload
    sudo chmod 777 /srv/nfs/diode_upload
    ```

### 7.2. Định nghĩa Thư mục Chia sẻ (`/etc/exports`)

Đây là nơi bạn quy định thư mục nào được chia sẻ và ai được phép truy cập.

1.  Mở file `/etc/exports`:
    ```bash
    sudo nano /etc/exports
    ```

2.  Thêm dòng sau vào cuối file. Dòng này sẽ chia sẻ thư mục cho các máy client trong dải IP `10.10.1.0/24`.

    ```
    /srv/nfs/diode_upload    10.10.1.0/24(rw,sync,no_subtree_check)
    ```
    **Lưu ý quan trọng:** Hãy thay đổi `10.10.1.0/24` thành dải IP mạng của các máy client, hoặc một địa chỉ IP cụ thể để tăng cường bảo mật.

3.  Áp dụng các thay đổi cấu hình:
    ```bash
    sudo exportfs -a
    ```

### 7.3. Cấu hình Tường lửa (UFW)

Cho phép các client đã được định nghĩa ở trên kết nối đến dịch vụ NFS.
```bash
sudo ufw allow from 10.10.1.0/24 to any port nfs
```
*Hãy đảm bảo dải IP ở đây khớp với dải IP trong file `/etc/exports`.*

### 7.4. Tích hợp và Khởi động lại

1.  **Cập nhật Dịch vụ `diode-send`:**
    Sửa file `/etc/systemd/system/diode-send.service` để script `inet-send.py` theo dõi đúng thư mục mà NFS chia sẻ.
    ```ini
    [Service]
    ...
    ExecStart=/usr/bin/python3 /path/to/project/inet-send.py --directory /srv/nfs/diode_upload --target-subnet ...
    ...
    ```

2.  **Khởi động lại các dịch vụ:**
    ```bash
    # Tải lại cấu hình systemd nếu bạn vừa sửa file service
    sudo systemctl daemon-reload

    # Khởi động lại NFS Server
    sudo systemctl restart nfs-kernel-server

    # Khởi động lại dịch vụ gửi file của bạn
    sudo systemctl restart diode-send.service
    ```

### 7.5. Kiểm tra từ Máy Client (Linux)

1.  **Trên máy Client**, cài đặt gói NFS client:
    ```bash
    sudo apt update
    sudo apt install -y nfs-common
    ```

2.  **Trên máy Client**, tạo một thư mục để mount:
    ```bash
    sudo mkdir -p /mnt/diode_share
    ```

3.  **Trên máy Client**, mount thư mục chia sẻ từ máy TX Proxy:
    ```bash
    # Thay <IP_TX_PROXY> bằng IP của máy TX Proxy
    sudo mount <IP_TX_PROXY>:/srv/nfs/diode_upload /mnt/diode_share
    ```

4.  **Thực hiện kiểm tra:**
    Copy một file vào thư mục vừa mount.
    ```bash
    cp /path/to/some/testfile.txt /mnt/diode_share/
    ```
    File sẽ được script `inet-send.py` tự động phát hiện, gửi đi và xóa khỏi thư mục nguồn.

## 8. 📜 Cấu hình Chuyển tiếp Syslog (Syslog Forwarding)

Tính năng này cho phép thu thập nhật ký (log) từ các thiết bị trong mạng an toàn và gửi một chiều đến một máy chủ giám sát tập trung (SIEM, Syslog Server) mà không tạo ra rủi ro cho mạng an toàn.

Kiến trúc này sử dụng `socat` làm bộ chuyển tiếp UDP hiệu quả.

```
Mạng An toàn (OT)                                      Mạng Giám sát (IT)
+----------------+   +---------------+        +----------+        +---------------+   +-------------------+
| Firewall, PLC, |   |               |        |          |        |               |   |                   |
| Server, Switch +-->|   TX Proxy    +------->| Hardware +------->|   RX Proxy    +-->|  SIEM / Syslog    |
| (Gửi Syslog)   |   | (socat)       |        |  Diode   |        | (socat)       |   |    Server         |
+----------------+   +---------------+        +----------+        +---------------+   +-------------------+
```

### 8.1. Cấu hình trên Máy TX Proxy

Máy TX Proxy sẽ hoạt động như một điểm thu thập log trung gian.

1.  **Tạo file dịch vụ** `/etc/systemd/system/diode-syslog-tx.service`:
    ```ini
    [Unit]
    Description=Diode Syslog TX Forwarder (OT -> Diode)
    After=network-online.target
    Wants=network-online.target

    [Service]
    Type=simple
    ExecStart=/usr/bin/socat UDP4-LISTEN:514,fork UDP4-DATAGRAM:<IP_RX_PROXY>:514
    Restart=always
    RestartSec=5

    [Install]
    WantedBy=multi-user.target
    ```
    *Lưu ý: Thay thế `<IP_RX_PROXY>` bằng IP của máy RX Proxy.*

2.  **Kích hoạt dịch vụ:**
    ```bash
    sudo systemctl daemon-reload
    sudo systemctl enable --now diode-syslog-tx.service
    ```

### 8.2. Cấu hình trên Máy RX Proxy

Máy RX Proxy sẽ nhận log từ Diode và gửi đến máy chủ cuối cùng.

1.  **Tạo file dịch vụ** `/etc/systemd/system/diode-syslog-rx.service`:
    ```ini
    [Unit]
    Description=Diode Syslog RX Forwarder (Diode -> SIEM)
    After=network-online.target
    Wants=network-online.target

    [Service]
    Type=simple
    ExecStart=/usr/bin/socat UDP4-LISTEN:514,fork UDP4-DATAGRAM:<IP_SYSLOG_SERVER>:514
    Restart=always
    RestartSec=5

    [Install]
    WantedBy=multi-user.target
    ```
    *Lưu ý: Thay thế `<IP_SYSLOG_SERVER>` bằng IP của máy chủ Log/SIEM của bạn.*

2.  **Kích hoạt dịch vụ:**
    ```bash
    sudo systemctl daemon-reload
    sudo systemctl enable --now diode-syslog-rx.service
    ```

### 8.3. Cấu hình Thiết bị Nguồn

Bước cuối cùng là cấu hình tất cả các thiết bị mạng, máy chủ... trong mạng an toàn để gửi Syslog của chúng đến địa chỉ IP của máy TX Proxy trên cổng 514/UDP.
## 9. 🕒 Cấu hình Đồng bộ Thời gian (NTP)

Đồng bộ thời gian chính xác là yêu cầu tối quan trọng trong các hệ thống công nghiệp. Data Diode có thể được sử dụng để đồng bộ thời gian một cách an toàn từ một nguồn tin cậy ở mạng IT sang mạng an toàn OT.

**Lưu ý quan trọng:** Luồng dữ liệu cho NTP sẽ đi theo hướng **IT -> Diode -> OT**, ngược lại với luồng dữ liệu chính của hệ thống. Điều này có thể yêu cầu một Diode vật lý thứ hai hoặc một kênh riêng biệt được cấu hình cho chiều ngược lại.

Giao thức NTP tiêu chuẩn (hỏi-đáp) không hoạt động qua Diode. Thay vào đó, chúng ta sẽ sử dụng chế độ **NTP Broadcast** (phát quảng bá) một chiều.

### 9.1. Kiến trúc hoạt động

```
Mạng IT (Có Internet/GPS)                                  Mạng An toàn (OT)
+-------------------+   +---------------+        +----------+        +---------------+   +-------------------+
|                   |   |               |        |          |        |               |   |                   |
|  NTP Server      +-->|   TX Proxy    +------->| Hardware +------->|   RX Proxy    +-->| Các thiết bị OT   |
| (Broadcast Mode)  |   | (IT Side)     |        |  Diode   |        | (OT Side)     |   | (Lắng nghe bcast) |
|                   |   |               |        | (IT->OT) |        |               |   |                   |
+-------------------+   +---------------+        +----------+        +---------------+   +-------------------+
```

### 9.2. Hướng dẫn Cài đặt (Sử dụng `chrony`)

`chrony` là dịch vụ NTP mặc định trên các phiên bản Ubuntu mới.

#### **a. Cấu hình NTP Server (Trên một máy chủ ở mạng IT)**

Máy chủ này sẽ lấy thời gian chuẩn và phát quảng bá ra mạng.

1.  **Cài đặt `chrony`:**
    ```bash
    sudo apt update
    sudo apt install -y chrony
    ```
2.  **Chỉnh sửa file `/etc/chrony/chrony.conf`:**
    ```ini
    # Đồng bộ với các server internet (hoặc nguồn thời gian nội bộ khác)
    pool pool.ntp.org iburst

    # Cho phép các máy trong mạng IT truy vấn thời gian
    allow 192.168.1.0/24 # <-- Thay bằng dải IP mạng IT của bạn

    # Phát quảng bá các gói tin NTP ra toàn mạng
    broadcast 255.255.255.255
    ```
3.  **Khởi động lại dịch vụ:**
    ```bash
    sudo systemctl restart chrony
    ```

#### **b. Cấu hình Diode Proxies (Chuyển tiếp NTP)**

Hai máy Proxy cần chuyển tiếp các gói tin NTP (cổng UDP 123). Bạn có thể tạo các dịch vụ `systemd` đơn giản để chạy lệnh `socat` tương tự như đã làm với Syslog, nhưng cho cổng 123.

* **TX Proxy (phía IT):** Cần nhận gói broadcast và gửi đến RX Proxy.
* **RX Proxy (phía OT):** Cần nhận gói từ TX Proxy và phát broadcast ra mạng OT.

#### **c. Cấu hình NTP Client (Trên các thiết bị mạng OT)**

Tất cả các máy cần đồng bộ thời gian trong mạng OT sẽ được cấu hình làm client lắng nghe broadcast.

1.  **Cài đặt `chrony`** trên các máy client.
2.  **Chỉnh sửa file `/etc/chrony/chrony.conf`:**
    * Xóa hoặc vô hiệu hóa tất cả các dòng `server` hoặc `pool` có sẵn.
    * Thêm dòng duy nhất sau:
    ```ini
    broadcastclient
    ```
3.  **Khởi động lại dịch vụ:**
    ```bash
    sudo systemctl restart chrony
    ```
Sau khi hoàn tất, các thiết bị trong mạng OT sẽ tự động đồng bộ thời gian mà vẫn duy trì được sự cách ly an toàn khỏi mạng IT.


---
## 📄 License

Dự án này được cấp phép dưới Giấy phép MIT. Xem file `LICENSE` để biết chi tiết.