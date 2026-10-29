#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/lenovo/lapis

# Inherit from the common SM8650 board configuration.
include device/lenovo/sm8650-common/BoardConfigCommon.mk

# Assert
TARGET_OTA_ASSERT_DEVICE := lapis,TB520FU

# Display
TARGET_SCREEN_DENSITY := 340

# DTB / DTBO
# kernel/lenovo/sm8650-devicetrees/lenovo builds the four lapis-pineapple*
# base dtbs of the stock vendor_boot dtb, the two board overlays of the stock
# dtbo.img (avf-overlay, lapis-qrd-overlay) and, in lenovo/techpack, the
# component overlays that merge_dtbs.py folds into the bases.
TARGET_MERGE_DTBS_WILDCARD := lapis-pineapple*
TARGET_MERGE_DTBOS_WILDCARD := *-overlay

# Kernel
TARGET_KERNEL_CONFIG := \
    gki_defconfig \
    vendor/lapis_GKI.config

# External modules (kernel/lenovo/sm8650-modules), in dependency order.
TARGET_KERNEL_EXT_MODULE_ROOT := kernel/lenovo/sm8650-modules
TARGET_KERNEL_EXT_MODULES := \
    qcom/opensource/mmrm-driver \
    qcom/opensource/mm-drivers/hw_fence \
    qcom/opensource/mm-drivers/msm_ext_display \
    qcom/opensource/mm-drivers/sync_fence \
    qcom/opensource/securemsm-kernel \
    qcom/opensource/audio-kernel \
    qcom/opensource/synx-kernel \
    qcom/opensource/camera-kernel \
    qcom/opensource/datarmnet-ext/mem \
    qcom/opensource/dataipa/drivers/platform/msm \
    qcom/opensource/datarmnet/core \
    qcom/opensource/datarmnet-ext/aps \
    qcom/opensource/datarmnet-ext/offload \
    qcom/opensource/datarmnet-ext/shs \
    qcom/opensource/datarmnet-ext/perf \
    qcom/opensource/datarmnet-ext/perf_tether \
    qcom/opensource/datarmnet-ext/sch \
    qcom/opensource/datarmnet-ext/wlan \
    qcom/opensource/display-drivers/msm \
    qcom/opensource/dsp-kernel \
    qcom/opensource/eva-kernel \
    qcom/opensource/video-driver \
    qcom/opensource/graphics-kernel \
    qcom/opensource/wlan/platform \
    qcom/opensource/wlan/qcacld-3.0/.kiwi_v2 \
    qcom/opensource/wlan/qcacld-3.0/.peach \
    qcom/opensource/wlan/qcacld-3.0/.qca6750 \
    qcom/opensource/bt-kernel \
    qcom/opensource/spu-kernel \
    qcom/opensource/mm-sys-kernel/ubwcp \
    qcom/opensource/fingerprint \
    qcom/opensource/touch-drivers \
    nxp/opensource/driver

# camera-kernel picks its pineapple config from BOARD_PLATFORM, which
# vendor/lineage does not pass to the external module builds.
TARGET_KERNEL_ADDITIONAL_FLAGS := \
    BOARD_PLATFORM=pineapple \
    TARGET_BOARD_PLATFORM=pineapple

# Kernel modules (stock load order and blocklists)
BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE := $(DEVICE_PATH)/modules/modules.blocklist
BOARD_VENDOR_KERNEL_MODULES_LOAD := $(strip $(shell grep -h '^[A-Za-z0-9_]' $(DEVICE_PATH)/modules/vendor_dlkm.modules.load))

BOARD_VENDOR_RAMDISK_KERNEL_MODULES_BLOCKLIST_FILE := $(BOARD_VENDOR_KERNEL_MODULES_BLOCKLIST_FILE)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(strip $(shell grep -h '^[A-Za-z0-9_]' $(DEVICE_PATH)/modules/vendor_boot.modules.load))
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell grep -h '^[A-Za-z0-9_]' $(DEVICE_PATH)/modules/vendor_boot.modules.load.recovery))
BOOT_KERNEL_MODULES := $(BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD)

# ADSP loader chain of the recovery ramdisk (init.recovery.qcom.rc)
BOARD_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell grep -h '^[A-Za-z0-9_]' $(DEVICE_PATH)/modules/recovery.modules.load))
RECOVERY_KERNEL_MODULES := $(BOARD_RECOVERY_KERNEL_MODULES_LOAD)

# Properties
TARGET_PRODUCT_PROP += $(DEVICE_PATH)/product.prop
TARGET_SYSTEM_EXT_PROP += $(DEVICE_PATH)/system_ext.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/vendor.prop

# Recovery
# The panel is natively landscape but the Novatek touch reports portrait
# coordinates (the -twrp tree swaps X/Y and flips Y, which is this rotation).
TARGET_RECOVERY_DEFAULT_TOUCH_ROTATION := ROTATION_RIGHT

# Security
BOOT_SECURITY_PATCH := 2026-08-05
VENDOR_SECURITY_PATCH := $(BOOT_SECURITY_PATCH)

# SEPolicy
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor

# Verified Boot
# pvmfw is a prebuilt (radio/, proprietary-firmware.txt) with its own testkey
# footer; the bootloader looks up its descriptor in the vbmeta chain.
BOARD_AVB_MAKE_VBMETA_IMAGE_ARGS += --include_descriptors_from_image vendor/lenovo/lapis/radio/pvmfw.img

# Include the proprietary files BoardConfig.
include vendor/lenovo/lapis/BoardConfigVendor.mk
