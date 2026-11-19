package com.railway.setup.core;

/** Somewhere to write task output: a console panel on screen, plus logs/setup-app.log. */
public interface Output {

    enum Kind { TEXT, DIM, OK, WARN, ERROR, COMMAND, TITLE }

    void line(Kind kind, String text);
}
