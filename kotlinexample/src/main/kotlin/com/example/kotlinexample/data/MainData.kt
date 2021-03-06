package com.example.kotlinexample.data

import jp.bglb.bonboru.flux.processor.annotation.ObservableClass
import jp.bglb.bonboru.flux.processor.annotation.ObservableField

/**
 * Created by tetsuya on 2016/06/25.
 */
@ObservableClass
data class MainData(@ObservableField var mesage: String = "",
    @ObservableField var progressVisibility: Boolean = false
//    @ObservableField var is_test: String = "",
//    @ObservableField var isTest: String = "",
//    @ObservableField var isaTest: String = ""
) {

  fun isProgressVisibility(): Boolean {
    return progressVisibility
  }
}
