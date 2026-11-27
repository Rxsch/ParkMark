package com.example.parkmark.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Serialize reads and writes so rapid actions cannot save stale or partially updated records.
public final class ParkRepository {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static volatile ParkRepository instance;
    private final ParkDatabase database;
    private final Context context;
    private final MutableLiveData<ParkingState> state = new MutableLiveData<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface Operation { void run(ParkDao dao) throws Exception; }
    public interface Completion { void done(Exception error); }

    private ParkRepository(Context context) {
        this.context = context.getApplicationContext();
        database = ParkDatabase.get(context);
    }

    public static synchronized ParkRepository get(Context context) {
        if (instance == null) instance = new ParkRepository(context);
        return instance;
    }

    public LiveData<ParkingState> state() { return state; }

    // Initialize one local profile and preserve the original app's saved parking data.
    public void refresh(Completion completion) {
        change(dao -> LegacyParkingImporter.migrate(dao,
                context.getSharedPreferences("ParkMarkParking", Context.MODE_PRIVATE)), completion);
    }

    public void change(Operation operation, Completion completion) {
        IO.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    try { operation.run(database.dao()); }
                    catch (Exception error) { throw new OperationException(error); }
                });
                // Alarm reconciliation shares the same serial queue as database updates.
                try { com.example.parkmark.reminders.Reminders.reconcile(context, database.dao()); }
                catch (RuntimeException error) { android.util.Log.e("ParkMark", "Could not reconcile reminders", error); }
                ParkingState snapshot = new ParkingState(database.dao());
                main.post(() -> {
                    state.setValue(snapshot);
                    if (completion != null) completion.done(null);
                });
            } catch (Exception error) {
                main.post(() -> { if (completion != null) completion.done(error); });
            }
        });
    }

    private static class OperationException extends RuntimeException {
        OperationException(Exception cause) { super(cause); }
    }
}
