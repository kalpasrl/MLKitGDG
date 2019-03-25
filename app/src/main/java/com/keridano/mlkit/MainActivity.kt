package com.keridano.mlkit

import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var scrollRange = -1
        var isShow = true

        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, i ->
            run {
                if (scrollRange == -1) {
                    scrollRange = appBarLayout.totalScrollRange
                }
                if (scrollRange + i == 0) {
                    collapsingToolbar.title = getString(R.string.ml_kit_codelab)
                    isShow = true
                } else if (isShow) {
                    collapsingToolbar.title = " "
                    isShow = false
                }
            }
        })

        initRecyclerView()
    }

    private fun initRecyclerView() {
        main_activity_recycler_view.layoutManager = LinearLayoutManager(this)
        main_activity_recycler_view.adapter = MainActivityAdapter(this)
    }
}
