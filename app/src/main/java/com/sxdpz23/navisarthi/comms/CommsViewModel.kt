package com.sxdpz23.navisarthi.comms

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class CommsViewModel(application: Application) : AndroidViewModel(application) {
    val repository = CommsRepository.get(application)
    val state = repository.state
}
