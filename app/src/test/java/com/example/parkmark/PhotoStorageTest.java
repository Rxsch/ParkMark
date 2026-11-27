package com.example.parkmark;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import com.example.parkmark.util.PhotoStorage;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

// A private photo must still open after the selected source file disappears.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PhotoStorageTest {
    @Test public void importedPhotoSurvivesSourceDeletionAndSupportsRemoval() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File source = new File(context.getCacheDir(), "camera-test.jpg");
        Bitmap original = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(source)) { original.compress(Bitmap.CompressFormat.JPEG, 90, output); }
        original.recycle();
        String path = PhotoStorage.importPhoto(context, Uri.fromFile(source));
        assertTrue(source.delete());
        Bitmap saved = PhotoStorage.decode(context, path, 1024);
        assertTrue(saved.getWidth() <= 1024);
        assertTrue(saved.getHeight() <= 1024);
        saved.recycle();
        assertTrue(new File(context.getFilesDir(), path).exists());
        PhotoStorage.delete(context, path);
        assertFalse(new File(context.getFilesDir(), path).exists());
    }
}
