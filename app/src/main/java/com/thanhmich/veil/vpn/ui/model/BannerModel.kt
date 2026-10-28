package com.thanhmich.veil.vpn.ui.model

import androidx.annotation.StringRes

enum class BannerVariant { Info, Warning, Error, Offline }

/** Hành động khi chạm nút của banner; ViewModel map sang lệnh cụ thể. */
enum class BannerAction {
    /** VpnConnectionManager.connect() — NoNetwork / PermissionDenied / AllServersFailed. */
    RetryConnect,

    /** VpnConnectionManager.connect() — ConnectionLost ("Kết nối lại"). */
    Reconnect,

    /** VpnConnectionManager.reconnect() — PendingSwitch ("Chuyển ngay"). */
    SwitchNow,

    /** ServerRepo.refresh() — catalog Failed, không có data. */
    RefreshCatalog,
}

/**
 * Banner thông báo trong UI-state. Text được resolve ở tầng Compose:
 * `stringResource(textRes, *args.toTypedArray())`.
 *
 * @param actionRes nhãn nút (null → không có nút). Khi khác null thì [action] khác null.
 */
data class BannerModel(
    val variant: BannerVariant,
    @param:StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    @param:StringRes val actionRes: Int? = null,
    val action: BannerAction? = null,
)
