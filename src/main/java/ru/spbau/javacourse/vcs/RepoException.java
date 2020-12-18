package ru.spbau.javacourse.vcs;

/**
 * Created by svloyso on 27.09.16.
 */
public class RepoException extends Exception {
    public RepoException(String message) {
        super(message);
    }
    public RepoException(Exception cause) {
        super(cause);
    }
}
