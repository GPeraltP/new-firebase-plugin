package org.apache.cordova.firebase;
public class Review {

    private String calification;
    private String comment;
    private String date;
    public Review() {
    }

    public Review(String calification, String date) {
        this.calification = calification;
        this.date = date;
    }

    public Review(String calification, String comment, String date) {
        this.calification = calification;
        this.comment = comment;
        this.date = date;
    }

    public String getCalification() {
        return calification;
    }

    public void setCalification(String calification) {
        this.calification = calification;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

}