package org.futo.inputmethod.gestures;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.webkit.WebView;
import android.widget.EditText;

/** Lives in the test APK so editor IPC goes through Android's input method framework. */
public class GestureEditorActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (getIntent().getBooleanExtra("web", false)) {
            WebView web = new WebView(this);
            setContentView(web);
            web.loadDataWithBaseURL(null, "<meta name='viewport' content='width=device-width'>"
                    + "<textarea autocapitalize='off' aria-label='Gesture editor' style='width:95%;height:150px'></textarea>",
                    "text/html", "utf-8", null);
        } else {
            EditText field = new EditText(this);
            field.setContentDescription("Gesture editor");
            field.setGravity(Gravity.TOP);
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            setContentView(field);
        }
    }
}
