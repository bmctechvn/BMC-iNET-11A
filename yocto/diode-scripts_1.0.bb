
DESCRIPTION = "Custom scripts and services for Data Diode"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Thêm dòng này để chỉ định thư mục mã nguồn và sửa cảnh báo "S variable doesn't exist"
S = "${WORKDIR}/sources-unpack"
# Liệt kê tất cả các file bạn đã copy vào thư mục files/
SRC_URI = " \
    file://inet_push \
    file://inet_receive \
    file://inet_tcp_rx \
    file://diode-receive.service \
    file://diode-tcp-rx.service \
    file://diode-udp-rx.service \
    file://static-arp.service \
    file://diode-push.service \
"

# Kế thừa lớp systemd để quản lý service
inherit systemd

# Liệt kê các service cần được enable lúc khởi động
SYSTEMD_SERVICE:${PN} = " \
        diode-tcp-rx.service \
        diode-udp-rx.service \
        static-arp.service \
        diode-receive.service \
        diode-push.service \
"
do_install() {
    # Tạo thư mục đích trên image
    install -d ${D}/usr/local/bin/diode
    install -d ${D}${systemd_unitdir}/system

    # Cài đặt các script Python
    install -m 0755 ${S}/inet_receive ${D}/usr/local/bin/diode/
    install -m 0755 ${S}/inet_push ${D}/usr/local/bin/diode/
    install -m 0755 ${S}/inet_tcp_rx ${D}/usr/local/bin/diode/
    #install -m 0755 ${WORKDIR}/inet_receive ${D}/usr/local/bin/diode/
    # ...thêm các lệnh install cho các script khác...

    # Cài đặt các file service
    install -m 0644 ${S}/diode-tcp-rx.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${S}/diode-udp-rx.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${S}/static-arp.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${S}/diode-push.service ${D}${systemd_unitdir}/system/
    #install -m 0644 ${WORKDIR}/diode.service ${D}${systemd_unitdir}/system/
    # ...thêm các lệnh install cho các service khác...
}

FILES:${PN} += "/usr/local/bin/diode/*"
FILES:${PN} += "${systemd_unitdir}/system/*.service"