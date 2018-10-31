package org.mark.lib_tensorflow;

import android.graphics.Bitmap;

import java.util.List;

/**
 * Created by Mark on 2018/10/22
 */
public interface Classifier {

    public static class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final int id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float confidence;

        public Recognition(final int id, final String title, final Float confidence) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        @Override
        public String toString() {
            String resultString = "";
//            if (id != null) {
//                resultString += "[" + id + "] ";
//            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += getConfidenceString();
            }

            resultString += "\n";

            return resultString.trim();
        }

        public String getConfidenceString() {
            return String.format("(%.1f%%)\n", confidence * 100.0f);
        }
    }


    List<Recognition> recognizeImage(Bitmap bitmap);

    void close();

    int getWidth();

    int getHeight();
}