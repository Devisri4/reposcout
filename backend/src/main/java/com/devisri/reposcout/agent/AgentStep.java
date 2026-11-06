package com.devisri.reposcout.agent;

/**
 * One tool call the agent made, for the steps panel in the UI.
 *
 * @param number  1, 2, 3 ... in the order the calls happened
 * @param tool    listFiles, readFile or searchCode
 * @param input   what the agent asked for, e.g. {@code src/App.java lines 1-100}
 * @param summary short result, e.g. {@code 12 matches} or the error message
 * @param millis  how long the tool took
 */
public record AgentStep(int number, String tool, String input, String summary, long millis) {
}
