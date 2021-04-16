package app.notifee.core;

import static app.notifee.core.ContextHolder.getApplicationContext;
import static app.notifee.core.ReceiverService.ACTION_PRESS_INTENT;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.graphics.drawable.IconCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkQuery;
import app.notifee.core.database.WorkDataEntity;
import app.notifee.core.database.WorkDataRepository;
import app.notifee.core.event.MainComponentEvent;
import app.notifee.core.event.NotificationEvent;
import app.notifee.core.model.IntervalTriggerModel;
import app.notifee.core.model.NotificationAndroidActionModel;
import app.notifee.core.model.NotificationAndroidModel;
import app.notifee.core.model.NotificationAndroidPressActionModel;
import app.notifee.core.model.NotificationAndroidStyleModel;
import app.notifee.core.model.NotificationModel;
import app.notifee.core.model.TimestampTriggerModel;
import app.notifee.core.utility.IntentUtils;
import app.notifee.core.utility.ObjectUtils;
import app.notifee.core.utility.ResourceUtils;
import app.notifee.core.utility.TextUtils;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class NotificationManager {
  private static final String TAG = "NotificationManager";
  private static final ExecutorService CACHED_THREAD_POOL = Executors.newCachedThreadPool();
  private static final int NOTIFICATION_TYPE_ALL = 0;
  private static final int NOTIFICATION_TYPE_DISPLAYED = 1;
  private static final int NOTIFICATION_TYPE_TRIGGER = 2;

  private static Task<NotificationCompat.Builder> notificationBundleToBuilder(
      NotificationModel notificationModel) {
    final NotificationAndroidModel androidModel = notificationModel.getAndroid();

    /*
     * Construct the initial NotificationCompat.Builder instance
     */
    Callable<NotificationCompat.Builder> builderCallable =
        () -> {
          Boolean hasCustomSound = false;
          NotificationCompat.Builder builder =
              new NotificationCompat.Builder(getApplicationContext(), androidModel.getChannelId());

          // must always keep at top
          builder.setExtras(notificationModel.getData());

          builder.setDeleteIntent(
              ReceiverService.createIntent(
                  ReceiverService.DELETE_INTENT,
                  new String[] {"notification"},
                  notificationModel.toBundle()));

          builder.setContentIntent(
              ReceiverService.createIntent(
                  ReceiverService.PRESS_INTENT,
                  new String[] {"notification", "pressAction"},
                  notificationModel.toBundle(),
                  androidModel.getPressAction()));

          if (notificationModel.getTitle() != null) {
            builder.setContentTitle(TextUtils.fromHtml(notificationModel.getTitle()));
          }

          if (notificationModel.getSubTitle() != null) {
            builder.setSubText(TextUtils.fromHtml(notificationModel.getSubTitle()));
          }

          if (notificationModel.getBody() != null) {
            builder.setContentText(TextUtils.fromHtml(notificationModel.getBody()));
          }

          if (androidModel.getBadgeIconType() != null) {
            builder.setBadgeIconType(androidModel.getBadgeIconType());
          }

          if (androidModel.getCategory() != null) {
            builder.setCategory(androidModel.getCategory());
          }

          if (androidModel.getColor() != null) {
            builder.setColor(androidModel.getColor());
          }

          builder.setColorized(androidModel.getColorized());

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(androidModel.getChronometerCountDown());
          }

          if (androidModel.getGroup() != null) {
            builder.setGroup(androidModel.getGroup());
          }

          builder.setGroupAlertBehavior(androidModel.getGroupAlertBehaviour());
          builder.setGroupSummary(androidModel.getGroupSummary());

          if (androidModel.getInputHistory() != null) {
            builder.setRemoteInputHistory(androidModel.getInputHistory());
          }

          if (androidModel.getLights() != null) {
            ArrayList<Integer> lights = androidModel.getLights();
            builder.setLights(lights.get(0), lights.get(1), lights.get(2));
          }

          builder.setLocalOnly(androidModel.getLocalOnly());

          if (androidModel.getNumber() != null) {
            builder.setNumber(androidModel.getNumber());
          }

          if (androidModel.getSound() != null) {
            Uri soundUri = ResourceUtils.getSoundUri(androidModel.getSound());
            if (soundUri != null) {
              hasCustomSound = true;
              builder.setSound(soundUri);
            } else {
              Logger.w(
                  TAG,
                  "Unable to retrieve sound for notification, sound was specified as: "
                      + androidModel.getSound());
            }
          }

          builder.setDefaults(androidModel.getDefaults(hasCustomSound));
          builder.setOngoing(androidModel.getOngoing());
          builder.setOnlyAlertOnce(androidModel.getOnlyAlertOnce());
          builder.setPriority(androidModel.getPriority());

          NotificationAndroidModel.AndroidProgress progress = androidModel.getProgress();
          if (progress != null) {
            builder.setProgress(
                progress.getMax(), progress.getCurrent(), progress.getIndeterminate());
          }

          if (androidModel.getShortcutId() != null) {
            builder.setShortcutId(androidModel.getShortcutId());
          }

          builder.setShowWhen(androidModel.getShowTimestamp());

          Integer smallIconId = androidModel.getSmallIcon();
          if (smallIconId != null) {
            Integer smallIconLevel = androidModel.getSmallIconLevel();
            if (smallIconLevel != null) {
              builder.setSmallIcon(smallIconId, smallIconLevel);
            } else {
              builder.setSmallIcon(smallIconId);
            }
          }

          if (androidModel.getSortKey() != null) {
            builder.setSortKey(androidModel.getSortKey());
          }

          if (androidModel.getTicker() != null) {
            builder.setTicker(androidModel.getTicker());
          }

          if (androidModel.getTimeoutAfter() != null) {
            builder.setTimeoutAfter(androidModel.getTimeoutAfter());
          }

          builder.setUsesChronometer(androidModel.getShowChronometer());

          long[] vibrationPattern = androidModel.getVibrationPattern();
          if (vibrationPattern.length > 0) builder.setVibrate(vibrationPattern);

          builder.setVisibility(androidModel.getVisibility());

          long timestamp = androidModel.getTimestamp();
          if (timestamp > -1) builder.setWhen(timestamp);

          builder.setAutoCancel(androidModel.getAutoCancel());

          return builder;
        };

    /*
     * A task continuation that fetches the largeIcon through Fresco, if specified.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> largeIconContinuation =
        task -> {
          NotificationCompat.Builder builder = task.getResult();

          if (androidModel.hasLargeIcon()) {
            String largeIcon = androidModel.getLargeIcon();
            Bitmap largeIconBitmap = null;

            try {
              largeIconBitmap =
                  Tasks.await(ResourceUtils.getImageBitmapFromUrl(largeIcon), 10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
              Logger.e(
                  TAG,
                  "Timeout occurred whilst trying to retrieve a largeIcon image: " + largeIcon,
                  e);
            } catch (Exception e) {
              Logger.e(
                  TAG,
                  "An error occurred whilst trying to retrieve a largeIcon image: " + largeIcon,
                  e);
            }

            if (largeIconBitmap != null) {
              if (androidModel.getCircularLargeIcon()) {
                largeIconBitmap = ResourceUtils.getCircularBitmap(largeIconBitmap);
              }

              builder.setLargeIcon(largeIconBitmap);
            }
          }

          return builder;
        };

    /*
     * A task continuation for full-screen action, if specified.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder>
        fullScreenActionContinuation =
            task -> {
              NotificationCompat.Builder builder = task.getResult();
              if (androidModel.hasFullScreenAction()) {
                NotificationAndroidPressActionModel fullScreenActionBundle =
                    androidModel.getFullScreenAction();

                String launchActivity = fullScreenActionBundle.getLaunchActivity();
                Class launchActivityClass = IntentUtils.getLaunchActivity(launchActivity);
                Intent launchIntent = new Intent(getApplicationContext(), launchActivityClass);
                if (fullScreenActionBundle.getLaunchActivityFlags() != -1) {
                  launchIntent.addFlags(fullScreenActionBundle.getLaunchActivityFlags());
                }

                if (fullScreenActionBundle.getMainComponent() != null) {
                  launchIntent.putExtra("mainComponent", fullScreenActionBundle.getMainComponent());
                  EventBus.postSticky(
                      new MainComponentEvent(fullScreenActionBundle.getMainComponent()));
                }

                PendingIntent fullScreenPendingIntent =
                    PendingIntent.getActivity(
                        getApplicationContext(),
                        notificationModel.getHashCode(),
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setFullScreenIntent(fullScreenPendingIntent, true);
              }

              return builder;
            };

    /*
     * A task continuation that builds all actions, if any. Additionally fetches
     * icon bitmaps through Fresco.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> actionsContinuation =
        task -> {
          NotificationCompat.Builder builder = task.getResult();
          ArrayList<NotificationAndroidActionModel> actionBundles = androidModel.getActions();

          if (actionBundles == null) {
            return builder;
          }

          for (NotificationAndroidActionModel actionBundle : actionBundles) {
            PendingIntent pendingIntent =
                ReceiverService.createIntent(
                    ACTION_PRESS_INTENT,
                    new String[] {"notification", "pressAction"},
                    notificationModel.toBundle(),
                    actionBundle.getPressAction().toBundle());

            String icon = actionBundle.getIcon();
            Bitmap iconBitmap = null;

            if (icon != null) {
              try {
                iconBitmap =
                    Tasks.await(
                        ResourceUtils.getImageBitmapFromUrl(actionBundle.getIcon()),
                        10,
                        TimeUnit.SECONDS);
              } catch (TimeoutException e) {
                Logger.e(
                    TAG, "Timeout occurred whilst trying to retrieve an action icon: " + icon, e);
              } catch (Exception e) {
                Logger.e(
                    TAG, "An error occurred whilst trying to retrieve an action icon: " + icon, e);
              }
            }

            IconCompat iconCompat = null;
            if (iconBitmap != null) {
              iconCompat = IconCompat.createWithAdaptiveBitmap(iconBitmap);
            }

            NotificationCompat.Action.Builder actionBuilder =
                new NotificationCompat.Action.Builder(
                    iconCompat, TextUtils.fromHtml(actionBundle.getTitle()), pendingIntent);

            RemoteInput remoteInput = actionBundle.getRemoteInput(actionBuilder);
            if (remoteInput != null) {
              actionBuilder.addRemoteInput(remoteInput);
            }

            builder.addAction(actionBuilder.build());
          }

          return builder;
        };

    /*
     * A task continuation that builds the notification style, if any. Additionally
     * fetches any image bitmaps (e.g. Person image, or BigPicture image) through
     * Fresco.
     */
    Continuation<NotificationCompat.Builder, NotificationCompat.Builder> styleContinuation =
        task -> {
          NotificationCompat.Builder builder = task.getResult();
          NotificationAndroidStyleModel androidStyleBundle = androidModel.getStyle();
          if (androidStyleBundle == null) {
            return builder;
          }

          Task<NotificationCompat.Style> styleTask =
              androidStyleBundle.getStyleTask(CACHED_THREAD_POOL);
          if (styleTask == null) {
            return builder;
          }

          NotificationCompat.Style style = Tasks.await(styleTask);
          if (style != null) {
            builder.setStyle(style);
          }

          return builder;
        };

    return Tasks.call(CACHED_THREAD_POOL, builderCallable)
        // get a large image bitmap if largeIcon is set
        .continueWith(CACHED_THREAD_POOL, largeIconContinuation)
        // set full screen action, if fullScreenAction is set
        .continueWith(CACHED_THREAD_POOL, fullScreenActionContinuation)
        // build notification actions, tasks based to allow image fetching
        .continueWith(CACHED_THREAD_POOL, actionsContinuation)
        // build notification style, tasks based to allow image fetching
        .continueWith(CACHED_THREAD_POOL, styleContinuation);
  }

  static Task<Void> cancelNotification(
      @NonNull String notificationId, @NonNull int notificationType) {
    return Tasks.call(
        () -> {
          NotificationManagerCompat notificationManagerCompat =
              NotificationManagerCompat.from(getApplicationContext());

          if (notificationType == NOTIFICATION_TYPE_DISPLAYED
              || notificationType == NOTIFICATION_TYPE_ALL) {
            notificationManagerCompat.cancel(notificationId.hashCode());
          }

          if (notificationType == NOTIFICATION_TYPE_TRIGGER
              || notificationType == NOTIFICATION_TYPE_ALL) {
            WorkManager.getInstance(getApplicationContext())
                .cancelUniqueWork("trigger:" + notificationId);
          }

          // delete notification entry from database
          WorkDataRepository.getInstance(getApplicationContext()).deleteById(notificationId);
          return null;
        });
  }

  static Task<Void> cancelAllNotifications(@NonNull int notificationType) {
    return Tasks.call(
        () -> {
          NotificationManagerCompat notificationManagerCompat =
              NotificationManagerCompat.from(getApplicationContext());

          if (notificationType == NOTIFICATION_TYPE_DISPLAYED
              || notificationType == NOTIFICATION_TYPE_ALL) {
            notificationManagerCompat.cancelAll();
          }

          if (notificationType == NOTIFICATION_TYPE_TRIGGER
              || notificationType == NOTIFICATION_TYPE_ALL) {
            WorkManager workManager = WorkManager.getInstance(getApplicationContext());
            workManager.cancelAllWorkByTag(Worker.WORK_TYPE_NOTIFICATION_TRIGGER);

            // Remove all cancelled and finished work from its internal database
            // states include SUCCEEDED, FAILED and CANCELLED
            workManager.pruneWork();

            // delete all from database
            WorkDataRepository.getInstance(getApplicationContext()).deleteAll();
          }

          return null;
        });
  }

  static Task<Void> displayNotification(NotificationModel notificationModel) {
    return notificationBundleToBuilder(notificationModel)
        .continueWith(
            CACHED_THREAD_POOL,
            (task) -> {
              NotificationCompat.Builder builder = task.getResult();
              NotificationAndroidModel androidBundle = notificationModel.getAndroid();
              Notification notification = Objects.requireNonNull(builder).build();
              int hashCode = notificationModel.getHashCode();

              if (androidBundle.getAsForegroundService()) {
                ForegroundService.start(hashCode, notification, notificationModel.toBundle());
              } else {
                NotificationManagerCompat.from(getApplicationContext())
                    .notify(hashCode, notification);
              }

              EventBus.post(
                  new NotificationEvent(NotificationEvent.TYPE_DELIVERED, notificationModel));

              return null;
            });
  }

  static Task<Void> createTriggerNotification(
      NotificationModel notificationModel, Bundle triggerBundle) {
    return Tasks.call(
        CACHED_THREAD_POOL,
        () -> {
          int triggerType = (int) triggerBundle.getDouble("type");
          switch (triggerType) {
            case 0:
              createTimestampTriggerNotification(notificationModel, triggerBundle);
              break;
            case 1:
              createIntervalTriggerNotification(notificationModel, triggerBundle);
              break;
          }

          EventBus.post(
              new NotificationEvent(
                  NotificationEvent.TYPE_TRIGGER_NOTIFICATION_CREATED, notificationModel));

          return null;
        });
  }

  static void createIntervalTriggerNotification(
      NotificationModel notificationModel, Bundle triggerBundle) {
    IntervalTriggerModel trigger = IntervalTriggerModel.fromBundle(triggerBundle);
    String uniqueWorkName = "trigger:" + notificationModel.getId();
    WorkManager workManager = WorkManager.getInstance(getApplicationContext());

    Data.Builder workDataBuilder =
        new Data.Builder()
            .putString(Worker.KEY_WORK_TYPE, Worker.WORK_TYPE_NOTIFICATION_TRIGGER)
            .putString(Worker.KEY_WORK_REQUEST, Worker.WORK_REQUEST_PERIODIC)
            .putString("id", notificationModel.getId());

    WorkDataRepository.getInstance(getApplicationContext())
        .insertTriggerNotification(notificationModel, triggerBundle);

    long interval = trigger.getInterval();

    PeriodicWorkRequest.Builder workRequestBuilder;
    workRequestBuilder =
        new PeriodicWorkRequest.Builder(Worker.class, interval, trigger.getTimeUnit());

    workRequestBuilder.addTag(Worker.WORK_TYPE_NOTIFICATION_TRIGGER);
    workRequestBuilder.addTag(uniqueWorkName);
    workRequestBuilder.setInputData(workDataBuilder.build());
    workManager.enqueueUniquePeriodicWork(
        uniqueWorkName, ExistingPeriodicWorkPolicy.REPLACE, workRequestBuilder.build());
  }

  static void createTimestampTriggerNotification(
      NotificationModel notificationModel, Bundle triggerBundle) {
    TimestampTriggerModel trigger = TimestampTriggerModel.fromBundle(triggerBundle);

    String uniqueWorkName = "trigger:" + notificationModel.getId();
    WorkManager workManager = WorkManager.getInstance(getApplicationContext());
    long delay = trigger.getDelay();
    int interval = trigger.getInterval();

    Data.Builder workDataBuilder =
        new Data.Builder()
            .putString(Worker.KEY_WORK_TYPE, Worker.WORK_TYPE_NOTIFICATION_TRIGGER)
            .putString("id", notificationModel.getId());

    WorkDataRepository.getInstance(getApplicationContext())
        .insertTriggerNotification(notificationModel, triggerBundle);

    // One time trigger
    if (interval == -1) {
      OneTimeWorkRequest.Builder workRequestBuilder = new OneTimeWorkRequest.Builder(Worker.class);
      workRequestBuilder.addTag(Worker.WORK_TYPE_NOTIFICATION_TRIGGER);
      workRequestBuilder.addTag(uniqueWorkName);
      workDataBuilder.putString(Worker.KEY_WORK_REQUEST, Worker.WORK_REQUEST_ONE_TIME);
      workRequestBuilder.setInputData(workDataBuilder.build());
      workRequestBuilder.setInitialDelay(delay, TimeUnit.SECONDS);
      workManager.enqueueUniqueWork(
          uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequestBuilder.build());
    } else {
      PeriodicWorkRequest.Builder workRequestBuilder;

      workRequestBuilder =
          new PeriodicWorkRequest.Builder(
              Worker.class, trigger.getInterval(), trigger.getTimeUnit());

      workRequestBuilder.addTag(Worker.WORK_TYPE_NOTIFICATION_TRIGGER);
      workRequestBuilder.addTag(uniqueWorkName);
      workRequestBuilder.setInitialDelay(delay, TimeUnit.SECONDS);
      workDataBuilder.putString(Worker.KEY_WORK_REQUEST, Worker.WORK_REQUEST_PERIODIC);
      workRequestBuilder.setInputData(workDataBuilder.build());
      workManager.enqueueUniquePeriodicWork(
          uniqueWorkName, ExistingPeriodicWorkPolicy.REPLACE, workRequestBuilder.build());
    }
  }

  static Task<List<String>> getTriggerNotificationIds() {
    return Tasks.call(
        () -> {
          WorkQuery.Builder query =
              WorkQuery.Builder.fromTags(Arrays.asList(Worker.WORK_TYPE_NOTIFICATION_TRIGGER));
          query.addStates(Arrays.asList(WorkInfo.State.ENQUEUED));

          List<WorkInfo> workInfos =
              WorkManager.getInstance(getApplicationContext()).getWorkInfos(query.build()).get();

          if (workInfos.size() == 0) {
            return Collections.emptyList();
          }

          ArrayList<String> triggerNotificationIds = new ArrayList<String>(workInfos.size());
          for (WorkInfo workInfo : workInfos) {
            List<String> tags = new ArrayList<String>(workInfo.getTags());

            for (int i = 0; i < tags.size(); i = i + 1) {
              String uniqueName = tags.get(i);
              if (uniqueName.contains("trigger")) {
                String notificationId = uniqueName.replace("trigger:", "");
                triggerNotificationIds.add(notificationId);
                break;
              }
            }
          }

          return triggerNotificationIds;
        });
  }

  static void doScheduledWork(
      Data data, CallbackToFutureAdapter.Completer<ListenableWorker.Result> completer) {

    String id = data.getString("id");

    WorkDataRepository workDataRepository = new WorkDataRepository(getApplicationContext());

    Continuation<WorkDataEntity, Task<Void>> workContinuation =
        task -> {
          WorkDataEntity workDataEntity = task.getResult();

          byte[] notificationBytes;

          if (workDataEntity == null || workDataEntity.getNotification() == null) {
            // check if notification bundle is stored with Work Manager
            notificationBytes = data.getByteArray("notification");
            if (notificationBytes != null) {
              Logger.w(
                  TAG,
                  "The trigger notification was created using an older version, please consider"
                      + " recreating the notification.");
            } else {
              Logger.w(
                  TAG, "Attempted to handle doScheduledWork but no notification data was found.");
              completer.set(ListenableWorker.Result.success());
              return null;
            }
          } else {
            notificationBytes = workDataEntity.getNotification();
          }

          NotificationModel notificationModel =
              NotificationModel.fromBundle(ObjectUtils.bytesToBundle(notificationBytes));

          return NotificationManager.displayNotification(notificationModel);
        };

    workDataRepository
        .getWorkDataById(id)
        .continueWithTask(CACHED_THREAD_POOL, workContinuation)
        .addOnCompleteListener(
            task -> {
              completer.set(ListenableWorker.Result.success());

              if (!task.isSuccessful()) {
                Logger.e(TAG, "Failed to display notification", task.getException());
              } else {
                String workerRequestType = data.getString(Worker.KEY_WORK_REQUEST);
                if (workerRequestType != null
                    && workerRequestType.equals(Worker.WORK_REQUEST_ONE_TIME)) {
                  // delete database entry if work is a one-time request
                  WorkDataRepository.getInstance(getApplicationContext()).deleteById(id);
                }
              }
            });
  }
}
