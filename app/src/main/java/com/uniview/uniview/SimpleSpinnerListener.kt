package com.uniview.uniview

import android.view.View
import android.widget.AdapterView

class SimpleSpinnerListener(
    private val callback: () -> Unit
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        callback()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        callback()
    }
}
