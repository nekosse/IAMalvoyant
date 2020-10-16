package org.tensorflow.lite.examples.classification;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class TrainActivity extends Activity {

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private EditText edittext;
    private EditText edittext2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_train);

        Button button =(Button) this.findViewById(R.id.button);
        edittext =(EditText) this.findViewById(R.id.editText);
        edittext2 =(EditText) this.findViewById(R.id.editText2);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name=edittext.getText().toString();
                Intent  intent =new Intent(TrainActivity.this,ClassifierActivity.class);
                intent.putExtra("name",name);
                TrainActivity.this.setResult(0, intent);
                TrainActivity.this.finish();

            }
        });


    }

    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();


    }
}
