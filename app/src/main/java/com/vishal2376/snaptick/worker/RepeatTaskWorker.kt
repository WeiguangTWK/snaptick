package com.vishal2376.snaptick.worker

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vishal2376.snaptick.data.local.TaskDatabase
import com.vishal2376.snaptick.data.repositories.TaskRepository
import com.vishal2376.snaptick.util.Constants
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlin.math.max

class RepeatTaskWorker(val context: Context, params: WorkerParameters) :
	CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		try {
			val dayOfWeek = LocalDate.now().dayOfWeek.value - 1 // because mon-1,sun-7

			val db = Room.databaseBuilder(
				context.applicationContext,
				TaskDatabase::class.java,
				"local_db"
			).build()

			val repository = TaskRepository(db.taskDao())
			val taskList = repository.getLastRepeatedTasks()

			taskList.forEach { task ->
				//repeat days of week
				val repeatWeekDays = task.getRepeatWeekList()
				if (repeatWeekDays.contains(dayOfWeek)) {
					if (task.reminder) {

						//cancel old notification request
						WorkManager.getInstance(context.applicationContext)
							.cancelAllWorkByTag(task.uuid)

						//calculate delay
						val startTimeSec = task.startTime.toSecondOfDay()
						val currentTimeSec = LocalTime.now().toSecondOfDay()
						val delaySec = max(startTimeSec - currentTimeSec, 0)
						
						if (delaySec > 0 || startTimeSec < 60) {
							val data = Data.Builder().putString(Constants.TASK_UUID, task.uuid)
								.putString(Constants.TASK_TITLE, task.title)
								.putString(Constants.TASK_TIME, task.getFormattedTime())
								.build()

							// new notification request
							val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
								.setInitialDelay(delaySec.toLong(), TimeUnit.SECONDS)
								.setInputData(data)
								.addTag(task.uuid)
								.build()

							WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
						}
					}

					//update task
					val newTask = task.copy(
						isCompleted = false,
						date = LocalDate.now(),
						pomodoroTimer = -1
					)
					repository.updateTask(newTask)
				}
			}

			db.close()
			return Result.success()
		} catch (e: Exception) {
			Log.e("@@@", "doWork: Error : ${e.message}")
		}
		return Result.failure()
	}
}