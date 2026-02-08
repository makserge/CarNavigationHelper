package com.smsoft.carnavigationhelper.data

import com.smsoft.carnavigationhelper.R

interface NamedOption {
    val resId: Int
}

enum class PlayerType(override val resId: Int) : NamedOption {
    INTERNAL(R.string.player_type_internal),
    AIMP(R.string.player_type_aimp);

    companion object {
        fun fromName(playerType: String): PlayerType {
            val item = entries.filter {
                it.name == playerType
            }
            return item[0]
        }
    }
}