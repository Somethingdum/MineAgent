package me.sailex.altoclef;

import me.sailex.altoclef.tasksystem.Task;
import me.sailex.altoclef.tasksystem.TaskChain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandStatusLogger {

    private final AltoClefController controller;
    private boolean isDebug = false;

    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.from(ZoneOffset.of("+00:00")));
    //For the ingame timer
    private long timeRunning;
        private long lastTime = 0;
    private int tickCounter = 0;

    public CommandStatusLogger(AltoClefController controller) {
        this.controller = controller;
    }

    public void tick() {
        tickCounter++;
        if (isDebug && tickCounter >= 60) { //every 3sec
            tickCounter = 0;
            logTasks();
        }
    }

    private void logTasks() {
        List<Task> tasks = Collections.emptyList();
        TaskChain currentTaskChain = controller.getTaskRunner().getCurrentTaskChain();
        if (currentTaskChain != null) {
            tasks = new ArrayList<>(currentTaskChain.getTasks());
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(controller.getTaskRunner().statusReport);

        if (tasks.isEmpty()) {
            if (controller.getTaskRunner().isActive()) {
                stringBuilder.append(" (no task running) ");
            }
            if (lastTime + 10000 < Instant.now().toEpochMilli()) {
                timeRunning = Instant.now().toEpochMilli();
            }
            return;
        }

        lastTime = Instant.now().toEpochMilli();
        String realTime = DATE_TIME_FORMATTER.format(Instant.now().minusMillis(timeRunning));
        stringBuilder.append("<").append(realTime).append(">");

        int maxLines = 10;
        if (tasks.size() <= maxLines) {
            for (Task task : tasks) {
                String taskName = task.getClass().getSimpleName() + " ";
                stringBuilder.append(taskName);
                stringBuilder.append(task);
                stringBuilder.append("\n");
            }
        }
        controller.log(stringBuilder.toString());
    }

    public boolean isDebug() {
        return isDebug;
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

}
