package com.smsoft.carnavigationhelper.data

import com.smsoft.carnavigationhelper.R

enum class LocationType(val resId: Int) {
    UNKNOWN(R.string.location_type_unknown),
    HOME(R.string.location_type_home),
    WORK(R.string.location_type_work);
}