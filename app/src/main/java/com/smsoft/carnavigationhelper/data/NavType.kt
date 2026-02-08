package com.smsoft.carnavigationhelper.data

import com.smsoft.carnavigationhelper.R

enum class NavType(override val resId: Int) : NamedOption {
    WAZE(R.string.nav_type_waze),
    IGO(R.string.nav_type_igo);

    companion object {
        fun fromName(navType: String): NavType {
            val item = NavType.entries.filter {
                it.name == navType
            }
            return item[0]
        }
    }
}