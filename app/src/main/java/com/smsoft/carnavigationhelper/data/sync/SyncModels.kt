package com.smsoft.carnavigationhelper.data.sync

import androidx.annotation.StringRes
import com.smsoft.carnavigationhelper.R
import kotlinx.serialization.Serializable

// One file as listed by the add-on's sync API (GET /api/v1/files)
@Serializable
data class RemoteFile(
    val name: String,
    val size: Long,
    val mtime: Long = 0,
    val sha256: String? = null,
)

// Why a sync failed, with the text shown in the settings screen
enum class SyncFailure(@StringRes val messageRes: Int) {
    NOT_CONFIGURED(R.string.sync_not_configured),
    BAD_ADDRESS(R.string.sync_bad_address),
    WRONG_TOKEN(R.string.sync_wrong_token),
    NO_FOLDER_ACCESS(R.string.sync_no_folder_access),
    SERVER_ERROR(R.string.sync_server_error),
    BAD_ANSWER(R.string.sync_bad_answer),
}

// retryable = worth trying again later (server down, server error); otherwise the setup must be fixed
class SyncException(val reason: SyncFailure, val retryable: Boolean) : Exception(reason.name)

data class SyncResult(
    val downloaded: Int,
    val removed: Int,
    val alreadyThere: Int,
    val conflicts: Int,
    val failed: Int,
    // Stopped early: the device storage would fall below the reserve
    val noSpace: Boolean,
)
