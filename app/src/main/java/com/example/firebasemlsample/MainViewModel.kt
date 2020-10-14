package com.example.firebasemlsample

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _recognizerType = MutableLiveData(RecogniserType.TEXT)
    val recognizerType: LiveData<RecogniserType> = _recognizerType

    fun changeNextRecognizerType() {
        _recognizerType.value?.ordinal?.let { index ->
            val nextIndex = (index + 1) % RecogniserType.values().size
            val nextType = RecogniserType.values()[nextIndex]
            _recognizerType.value = nextType
        }
    }

}

enum class RecogniserType(val labelText: String) {
    TEXT("Text Recognition"),
    LABEL("Image Labeling"),
    LANDMARK("Landmark Recognition"),
    ;
}