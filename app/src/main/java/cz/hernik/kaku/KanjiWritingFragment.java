package cz.hernik.kaku;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import cz.hernik.kaku.helper.KatakanaTable;

public class KanjiWritingFragment extends Fragment {

    private Dialog dialog;
    private Context c;
    private Dialog loadDialog;
    private View mainView;
    private EditText userInput;
    private int currentIndex = 0;
    private List<JSONObject> knownSentences;
    private SharedPreferences pref;
    private int yourPoints = 0;
    private int totalPoints = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_kanji_writing, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view,savedInstanceState);
        currentIndex = 0;
        c = getContext();
        pref = PreferenceManager.getDefaultSharedPreferences(c);

        if(pref.getBoolean("hiraganaedit",true)){
            userInput = view.findViewById(R.id.hiraganaEditText);
            userInput.setVisibility(View.VISIBLE);
            EditText plain = view.findViewById(R.id.answer);
            plain.setVisibility(View.GONE);
        }
        else{
            userInput = view.findViewById(R.id.answer);
            userInput.setVisibility(View.VISIBLE);
            EditText hira = view.findViewById(R.id.hiraganaEditText);
            hira.setVisibility(View.GONE);
        }

        if(pref.getBoolean("count",false)){
            TextView points = view.findViewById(R.id.points_k);
            points.setText(String.format(getResources().getQuantityString(R.plurals.points,0),0,0));
        }

        // next onclick
        Button next = view.findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                next.setVisibility(View.GONE);
                TextView correct = mainView.findViewById(R.id.correct_wrong);
                correct.setText("");
                TextView english = mainView.findViewById(R.id.english);
                english.setText("");
                currentIndex++;
                if(currentIndex>knownSentences.size()){ // check if there are sentences in list
                    AlertDialog.Builder d = new AlertDialog.Builder(c);
                    d.setTitle(getString(R.string.out_of_sentences));
                    d.setMessage(getString(R.string.no_more));
                    d.setCancelable(false);
                    d.setPositiveButton("Ok", (dialog, which) -> {
                        Fragment f = null;
                        try {
                            f = SecondFragment.class.newInstance();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        MainActivity.changeChecked();
                        getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.flContent,f).commit();
                    });
                    d.create().show();
                    return;
                }
                try {
                    displayKanji(knownSentences.get(currentIndex).getString("FIELD1"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                userInput.setText("");

                Button submit = mainView.findViewById(R.id.submit);
                submit.setClickable(true);
            }
        });

        // get kanji list from args of previous fragment
        String knownKanji = this.getArguments().getString("kanjilist");
        AssetManager assetManager = getActivity().getAssets();
        mainView = view;
        // load tatoeba strings
        Thread th = new Thread(()->{
            Handler handler = new Handler(getActivity().getMainLooper());
            String text = null;

            // build and show loading sentences
            View alertView = getLayoutInflater().inflate(R.layout.progress,null);
            TextView info = alertView.findViewById(R.id.loading_msg);
            info.setText(getActivity().getString(R.string.msg_loading_sentences));
            AlertDialog.Builder builder1 = new AlertDialog.Builder(c);
            builder1.setView(alertView);
            builder1.setCancelable(false);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadDialog = builder1.create();
                    loadDialog.show();
                }
            });
            JSONArray sentences = null;
            try {
                InputStream stream = assetManager.open("tatoeba_16_4_21.json");

                try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
                    text = scanner.useDelimiter("\\A").next();
                }
                sentences = new JSONArray(text);
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            // hide loading dialog
            getActivity().runOnUiThread(() -> loadDialog.dismiss());

            // show filtering
            View aV = getLayoutInflater().inflate(R.layout.progress,null);
            TextView loading = aV.findViewById(R.id.loading_msg);
            loading.setText(String.format(getActivity().getString(R.string.msg_filtering),String.valueOf(sentences.length())));
            AlertDialog.Builder builder2 = new AlertDialog.Builder(c);
            builder2.setView(aV);
            builder2.setCancelable(false);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog = builder2.create();
                    dialog.show();
                }
            });
            // get strings with known kanji
            knownSentences = new ArrayList<>();
            for(int i = 0;i<sentences.length();i++){
                String sentence = null;
                try {
                    sentence = sentences.getJSONObject(i).getString("FIELD1");
                    final Pattern pattern = Pattern.compile("[\u3005\u3400-\u4DB5\u4E00-\u9FCB\uF900-\uFA6A]", Pattern.MULTILINE); // <3 https://github.com/cubetastic33/sakubun/blob/a87d6cf9c686173663955ef93e82baaec5cbc0ec/static/scripts/known_kanji.js#L57
                    final Matcher matcher = pattern.matcher(sentence);
                    List<String> foundKanji = new ArrayList<>();
                    while(matcher.find()){
                        foundKanji.add(matcher.group(0));
                    }

                    if(foundKanji.size() > 0){
                        boolean add = false;
                        for (String kanji:foundKanji){
                            if(knownKanji.contains(kanji)) add = true;
                            else {
                                add = false;
                                break;
                            }
                        }
                        if(add){
                            knownSentences.add(sentences.getJSONObject(i));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Runnable runnable = () -> {
                try {
                    // randomize
                    Collections.shuffle(knownSentences);
                    snapBack();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            };
            // call back to main thread
            handler.post(runnable);
        });
        th.start();
    }

    private void snapBack() throws JSONException {
        String jpSentence = knownSentences.get(currentIndex).getString("FIELD1");

        //userInput = mainView.findViewById(R.id.hiraganaEditText);
        userInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event == null || event.getAction() != KeyEvent.ACTION_DOWN)
                    return false;
                try {
                    submit();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
        mainView.findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    submit();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        displayKanji(jpSentence);

        dialog.dismiss();
    }

    private void displayKanji(String jpSentence) {
        TextView sentenceDisplay = mainView.findViewById(R.id.hiragana);
        sentenceDisplay.setText(jpSentence);
    }


    private void submit() throws JSONException {
        MainActivity.hideKeyboardFrom(getContext(),mainView);
        String jpSentence = knownSentences.get(currentIndex).getString("FIELD1");
        String enSentence = knownSentences.get(currentIndex).getString("FIELD2");

        Thread convert = new Thread(new Runnable() {
            @Override
            public void run() {
                Tokenizer tokenizer = new Tokenizer() ;

                // convert japanese sentence to reading
                List<Token> tokens = tokenizer.tokenize(replaceNumbers(jpSentence));
                StringBuilder jpKata = new StringBuilder();
                for (Token token : tokens) {
                    jpKata.append(token.getReading());
                }

                // convert user's input to reading
                String input = userInput.getText().toString();
                List<Token> userTokens = tokenizer.tokenize(input);
                StringBuilder userKata = new StringBuilder();
                for (Token token: userTokens){
                    userKata.append(token.getReading());
                }

                String jpConv = jpKata.toString();
                String userConv = userKata.toString();

                // replace !?.
                if(!pref.getBoolean("punct", false)){
                    jpConv = jpConv.replaceAll("[！？。]","");
                    userConv = userConv.replaceAll("[！？。]","");
                }
                if(jpConv.equals(userConv)){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView correct = mainView.findViewById(R.id.correct_wrong);
                            correct.setText(Html.fromHtml(getString(R.string.correct)));

                            TextView english = mainView.findViewById(R.id.english);
                            english.setText(String.format(getString(R.string.english_meaning),enSentence));
                            Button next = mainView.findViewById(R.id.next);
                            next.setVisibility(View.VISIBLE);

                            Button submit = mainView.findViewById(R.id.submit);
                            submit.setClickable(false);

                            if(pref.getBoolean("count",false)){
                                TextView points = mainView.findViewById(R.id.points_k);
                                yourPoints++;
                                totalPoints++;
                                points.setText(String.format(getResources().getQuantityString(R.plurals.points,yourPoints), yourPoints, totalPoints));
                            }
                        }
                    });
                }
                else{
                    String finalJpConv = jpConv;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView correct = mainView.findViewById(R.id.correct_wrong);
                            correct.setText(Html.fromHtml(String.format(getString(R.string.wrong),KatakanaTable.toHiragana(finalJpConv))));
                            TextView english = mainView.findViewById(R.id.english);
                            english.setText(String.format(getString(R.string.english_meaning),enSentence));
                            Button next = mainView.findViewById(R.id.next);
                            next.setVisibility(View.VISIBLE);

                            Button submit = mainView.findViewById(R.id.submit);
                            submit.setClickable(false);
                            if(pref.getBoolean("count",false)){
                                TextView points = mainView.findViewById(R.id.points_k);
                                totalPoints++;
                                points.setText(String.format(getResources().getQuantityString(R.plurals.points,yourPoints), yourPoints, totalPoints));
                            }
                        }
                    });
                }
            }
        });
        convert.start();
    }

    public String replaceNumbers(String input){
        return input.replaceAll("0","０").replaceAll("1","１").replaceAll("2","２").replaceAll("3","３").replaceAll("4","４").replaceAll("5","５").replaceAll("6","６").replaceAll("7","７").replaceAll("8","８").replaceAll("9","９");
    }
}