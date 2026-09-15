package com.example.subserver;

import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class SubServerPlugin extends PluginBase {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private final Map<String, ManagedSubServer> subServers = new LinkedHashMap<>();
    private Path subServersRoot;

    @Override
    public void onEnable() {
        subServersRoot = getDataFolder().toPath().resolve("subservers");
        try {
            Files.createDirectories(subServersRoot);
            getLogger().info(TextFormat.GREEN + "Підсерверний плагін активовано: " + subServersRoot);
        } catch (IOException exception) {
            getLogger().error("Не вдалося створити папку підсерверів: " + exception.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Підсервери зупиняються тільки разом із основним сервером або явно через /ss stop.
        subServers.values().forEach(ManagedSubServer::stop);
        subServers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ss")) return false;
        if (args.length == 0) { sendUsage(sender); return true; }
        String action = args[0].toLowerCase();
        if (action.equals("l") || action.equals("list")) { listSubServers(sender); return true; }
        if (args.length < 2) {
            sender.sendMessage(TextFormat.RED + "Використання: /ss <команда> <назва>");
            return true;
        }
        String name = normalizeName(args[1]);
        if (name == null) {
            sender.sendMessage(TextFormat.RED + "Некоректна назва підсервера.");
            return true;
        }
        switch (action) {
            case "start" -> startSubServer(sender, name, false);
            case "startmain", "main" -> startSubServer(sender, name, true);
            case "stop" -> stopSubServer(sender, name);
            case "m" -> sendCommandToSubServer(sender, name, join(args, 2));
            default -> sendUsage(sender);
        }
        return true;
    }

    private void startSubServer(CommandSender sender, String name, boolean copyMainPlugins) {
        ManagedSubServer existing = subServers.get(name);
        if (existing != null && existing.isRunning()) {
            sender.sendMessage(TextFormat.YELLOW + "Підсервер " + name + " уже запущений.");
            return;
        }
        Path folder = subServersRoot.resolve(name);
        try {
            Files.createDirectories(folder);
            if (copyMainPlugins) copyMainPlugins(folder.resolve("plugins"));
            Path jar = findJar(folder);
            if (jar == null) {
                sender.sendMessage(TextFormat.RED + "У " + folder + " немає server.jar або *.jar.");
                return;
            }
            ManagedSubServer managed = new ManagedSubServer(name, folder, jar);
            managed.start();
            subServers.put(name, managed);
            sender.sendMessage(TextFormat.GREEN + "Підсервер " + name + " запущено.");
        } catch (IOException exception) {
            sender.sendMessage(TextFormat.RED + "Помилка запуску: " + exception.getMessage());
        }
    }

    private void copyMainPlugins(Path target) throws IOException {
        Path source = Path.of("plugins");
        Files.createDirectories(target);
        if (!Files.isDirectory(source)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(source, "*.jar")) {
            for (Path plugin : files) {
                if (plugin.getFileName().toString().equalsIgnoreCase("SubServer-1.0.0.jar")) continue;
                Files.copy(plugin, target.resolve(plugin.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void stopSubServer(CommandSender sender, String name) {
        ManagedSubServer managed = subServers.remove(name);
        if (managed == null || !managed.isRunning()) {
            sender.sendMessage(TextFormat.YELLOW + "Підсервер " + name + " не запущений.");
            return;
        }
        managed.stop();
        sender.sendMessage(TextFormat.GREEN + "Підсервер " + name + " зупинено.");
    }

    private void sendCommandToSubServer(CommandSender sender, String name, String message) {
        ManagedSubServer managed = subServers.get(name);
        if (managed == null || !managed.isRunning() || message.isBlank()) {
            sender.sendMessage(TextFormat.RED + "Підсервер не запущений або команда порожня.");
            return;
        }
        try {
            managed.sendCommand(message);
            sender.sendMessage(TextFormat.GREEN + "Команду надіслано.");
        } catch (IOException exception) {
            sender.sendMessage(TextFormat.RED + "Не вдалося надіслати команду: " + exception.getMessage());
        }
    }

    private void listSubServers(CommandSender sender) {
        if (subServers.isEmpty()) {
            sender.sendMessage(TextFormat.YELLOW + "Запущених підсерверів немає.");
            return;
        }
        subServers.forEach((name, server) -> sender.sendMessage(TextFormat.GREEN + name + TextFormat.WHITE
                + (server.isRunning() ? " - запущений" : " - зупинений")));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(TextFormat.GOLD + "/ss start <назва>" + TextFormat.WHITE + " - запустити підсервер");
        sender.sendMessage(TextFormat.GOLD + "/ss startmain <назва>" + TextFormat.WHITE + " - запустити з копією плагінів основного сервера");
        sender.sendMessage(TextFormat.GOLD + "/ss stop <назва>" + TextFormat.WHITE + " - зупинити підсервер");
        sender.sendMessage(TextFormat.GOLD + "/ss m <назва> <команда>" + TextFormat.WHITE + " - надіслати команду");
        sender.sendMessage(TextFormat.GOLD + "/ss list" + TextFormat.WHITE + " - список підсерверів");
    }

    private String normalizeName(String name) { return SAFE_NAME.matcher(name).matches() ? name : null; }

    private Path findJar(Path folder) throws IOException {
        Path preferred = folder.resolve("nukkit-1.0.jar");
        if (Files.isRegularFile(preferred)) return preferred;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.jar")) {
            for (Path file : files) return file;
        }
        return null;
    }

    private String join(String[] values, int start) {
        return start >= values.length ? "" : String.join(" ", Arrays.copyOfRange(values, start, values.length));
    }

    private static final class ManagedSubServer {
        private final String name;
        private final Path folder;
        private final Path jar;
        private volatile Process process;
        private BufferedWriter input;

        private ManagedSubServer(String name, Path folder, Path jar) {
            this.name = name;
            this.folder = folder;
            this.jar = jar;
        }

        private synchronized void start() throws IOException {
            ProcessBuilder builder = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-jar",
                    jar.getFileName().toString(), "nogui").directory(folder.toFile()).redirectErrorStream(true);
            process = builder.start();
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            Thread output = new Thread(this::readOutput, "subserver-" + name);
            output.setDaemon(true);
            output.start();
        }

        private synchronized void sendCommand(String command) throws IOException {
            if (!isRunning()) throw new IOException("процес уже завершився");
            input.write(command);
            input.newLine();
            input.flush();
        }

        private synchronized void stop() {
            if (!isRunning()) return;
            try { sendCommand("stop"); } catch (IOException exception) { process.destroy(); }
        }

        private boolean isRunning() { return process != null && process.isAlive(); }

        private void readOutput() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) System.out.println("[SubServer " + name + "] " + line);
            } catch (IOException ignored) { }
        }
    }
}
