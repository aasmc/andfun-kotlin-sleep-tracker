/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    // need this Job object to be able to create a unique context and be able to
    // cancel parent coroutine and all its children
    private var viewModelJob = Job()

    // will run on UI thread to update UI
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    // holds current night
    private var tonight = MutableLiveData<SleepNight?>()

    private val nights = database.getAllNights()

    /**
     * Sets the variable to true if tonight is null, i.e. no tracking of the sleep is in process
     */
    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }

    /**
     * Sets the variable to true if tonight is not null, i.e. tracking if sleep is in process
     */
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }

    /**
     * Sets the variable to true if there're any nights displayed
     */
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    // we need a string to be able to display it in a textView
    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }

    // Event for showing snackBar
    private val _showSnackBarEvent = MutableLiveData<Boolean>()

    // encapsulation for the event
    val showSnackBar: LiveData<Boolean>
        get() = _showSnackBarEvent

    fun doneShowingSnackBar() {
        _showSnackBarEvent.value = false
    }

    // event for navigating to SleepQualityFragment
    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

    // encapsulation for the event to provide only a getter
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    /**
     * Resets the event for navigating to SleepQualityFragment to null
     */
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    // initialize tonight variable when viewModel is created
    init {
        initializeTonight()
    }

    /**
     * Gets tonight value from the DB asynchronously using uiScope
     */
    private fun initializeTonight() {
        // get tonight from the db non blocking
        // launch the coroutine
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    /**
     * Gets tonight from the DB asynchronously
     * If endTime and startTime are not the same it means that the night tracking has completed
     * Therefore returns null
     */
    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    /**
     * Here we cancel all coroutines started on the UI thread
     */
    override fun onCleared() {
        super.onCleared()

        // cancel all coroutines launched in the viewModel
        viewModelJob.cancel()
    }

    /**
     * Click handler for button to start tracking
     */
    fun onStartTracking() {
        //use UI scope to update UI
        uiScope.launch {

            // create new Night with current time as the startTimeMilli
            val newNight = SleepNight()

            // insert the newNight into the DB using suspend function
            insert(newNight)

            // set the value of tonight to a newly created night
            tonight.value = getTonightFromDatabase()
        }
    }

    /**
     * Suspend function to insert night into the DB
     * Uses Dispatchers.IO to run coroutine on a separate thread
     */
    private suspend fun insert(night: SleepNight) {
        // use IO context to run coroutine on a separate thread from a threadPool
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }

    /**
     * Click handler for button to stop tracking
     * Updates tonight in the database to set endTimeMilli property
     * triggers navigation to the SleepQualityFragment by setting the value of the event to the night
     */
    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch

            oldNight.endTimeMilli = System.currentTimeMillis()

            update(oldNight)
            _navigateToSleepQuality.value = oldNight
        }
    }

    /**
     * Suspend function to update night in the DB
     */
    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    /**
     * Click handler for button to clear all nights
     * Sets tonight value to null
     */
    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackBarEvent.value = true // trigger the snackBar showing event
        }
    }

    /**
     * Suspend function that uses Dispatchers.IO to run coroutines on a separate thread
     * Clears the DB
     */
    private suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }
}

