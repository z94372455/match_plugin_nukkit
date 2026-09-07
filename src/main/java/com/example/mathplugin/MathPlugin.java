package com.example.mathplugin;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.util.*;
import java.util.stream.Collectors;

public class MathPlugin extends PluginBase implements Listener {

    private int currentAnswer = Integer.MIN_VALUE;
    private boolean taskActive = false;
    private Config leaderboardConfig;
    private final Map<String, Integer> leaderboard = new HashMap<>();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.getDataFolder().mkdirs();
        
        // Створення та завантаження файлу leaderboard.yml
        this.leaderboardConfig = new Config(this.getDataFolder() + "/leaderboard.yml", Config.YAML);
        for (String key : leaderboardConfig.getKeys(false)) {
            leaderboard.put(key, leaderboardConfig.getInt(key));
        }

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getLogger().info(TextFormat.GREEN + "MathPlugin для Nukkit успішно активовано!");
    }

    @Override
    public void onDisable() {
        saveLeaderboardData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("math")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(TextFormat.YELLOW + "=== Math Plugin ===");
            sender.sendMessage(TextFormat.GOLD + "/math start " + TextFormat.WHITE + "- Запустити нову задачу");
            sender.sendMessage(TextFormat.GOLD + "/math top " + TextFormat.WHITE + "- Переглянути топ гравців");
            sender.sendMessage(TextFormat.GOLD + "/math answer <число> " + TextFormat.WHITE + "- Надати відповідь");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
            case "генерувати":
                generateNewTask();
                sender.sendMessage(TextFormat.GREEN + "Нову задачу успішно згенеровано!");
                break;

            case "top":
            case "topboard":
            case "лідерборд":
                showLeaderboard(sender);
                break;

            case "answer":
            case "відповідь":
                if (args.length < 2) {
                    sender.sendMessage(TextFormat.RED + "Використання: /math answer <число>");
                    return true;
                }
                try {
                    int answer = Integer.parseInt(args[1]);
                    checkAnswer(sender, answer);
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextFormat.RED + "Будь ласка, вкажіть дійсне ціле число!");
                }
                break;

            default:
                sender.sendMessage(TextFormat.RED + "Невідома команда. Напишіть /math для виводу підказки.");
                break;
        }
        return true;
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        if (!taskActive) return;

        String message = event.getMessage().trim();
        try {
            int playerAnswer = Integer.parseInt(message);
            checkAnswer(event.getPlayer(), playerAnswer);
        } catch (NumberFormatException ignored) {
            // Гравець написав звичайне текстове повідомлення в чат
        }
    }

    private void generateNewTask() {
        Random random = new Random();
        int num1 = random.nextInt(50) + 1;
        int num2 = random.nextInt(50) + 1;
        int operation = random.nextInt(3); // 0: +, 1: -, 2: *

        String operatorSymbol;
        switch (operation) {
            case 0:
                operatorSymbol = "+";
                currentAnswer = num1 + num2;
                break;
            case 1:
                operatorSymbol = "-";
                currentAnswer = num1 - num2;
                break;
            default:
                num1 = random.nextInt(12) + 1;
                num2 = random.nextInt(12) + 1;
                operatorSymbol = "*";
                currentAnswer = num1 * num2;
                break;
        }

        taskActive = true;
        String taskMessage = TextFormat.LIGHT_PURPLE + "[Math] " + TextFormat.YELLOW + "Розв'яжіть приклад: " 
                + TextFormat.AQUA + num1 + " " + operatorSymbol + " " + num2 + " = ?";
        
        this.getServer().broadcastMessage(taskMessage);
        this.getLogger().info(taskMessage);
    }

    private synchronized void checkAnswer(CommandSender sender, int answer) {
        if (!taskActive) {
            sender.sendMessage(TextFormat.RED + "Наразі немає активної математичної задачі!");
            return;
        }

        if (answer == currentAnswer) {
            taskActive = false;
            String winnerName = sender.getName();

            int newScore = leaderboard.getOrDefault(winnerName, 0) + 1;
            leaderboard.put(winnerName, newScore);
            saveLeaderboardData();

            String winMessage = TextFormat.GREEN + "[Math] " + TextFormat.GOLD + winnerName 
                    + TextFormat.GREEN + " правильно розв'язав(ла) задачу! Відповідь: " + TextFormat.WHITE + currentAnswer;

            this.getServer().broadcastMessage(winMessage);
            this.getLogger().info(winMessage);
        } else {
            sender.sendMessage(TextFormat.RED + "Неправильна відповідь, спробуйте ще раз!");
        }
    }

    private void showLeaderboard(CommandSender sender) {
        if (leaderboard.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "Лідерборд поки що порожній.");
            return;
        }

        sender.sendMessage(TextFormat.GOLD + "=== ТОП-10 Математиків ===");

        List<Map.Entry<String, Integer>> sortedList = leaderboard.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sortedList) {
            sender.sendMessage(TextFormat.GREEN + "" + rank + ". " + TextFormat.WHITE + entry.getKey() 
                    + " — " + TextFormat.YELLOW + entry.getValue() + " розв'язаних задач");
            rank++;
        }
    }

    private void saveLeaderboardData() {
        for (Map.Entry<String, Integer> entry : leaderboard.entrySet()) {
            leaderboardConfig.set(entry.getKey(), entry.getValue());
        }
        leaderboardConfig.save();
    }
}