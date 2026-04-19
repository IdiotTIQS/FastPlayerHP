package cn.brocraft.fastPlayerHP.command;

import cn.brocraft.fastPlayerHP.FastPlayerHP;
import cn.brocraft.fastPlayerHP.service.HealthDisplayService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class FastPlayerHPCommand implements CommandExecutor, TabCompleter {

    private final FastPlayerHP plugin;
    private final HealthDisplayService displayService;

    public FastPlayerHPCommand(FastPlayerHP plugin, HealthDisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fastplayerhp.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§e/fphp reload");
            sender.sendMessage("§e/fphp toggle");
            sender.sendMessage("§e/fphp mode <hearts|full|toggle>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("§aFastPlayerHP config reloaded.");
            }
            case "toggle" -> {
                boolean next = !displayService.isEnabled();
                displayService.setEnabled(next);
                sender.sendMessage(next ? "§aFastPlayerHP enabled." : "§cFastPlayerHP disabled.");
            }
            case "mode" -> handleMode(sender, args);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }

        return true;
    }

    private void handleMode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eCurrent mode: " + displayService.getDisplayMode().name().toLowerCase());
            return;
        }

        String modeInput = args[1].toLowerCase();
        HealthDisplayService.DisplayMode mode;
        if ("toggle".equals(modeInput)) {
            mode = displayService.getDisplayMode().toggle();
        } else {
            mode = HealthDisplayService.DisplayMode.fromInput(modeInput);
        }

        displayService.setDisplayMode(mode);
        sender.sendMessage("§aDisplay mode -> " + mode.name().toLowerCase());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!sender.hasPermission("fastplayerhp.admin")) {
            return suggestions;
        }

        if (args.length == 1) {
            addIfMatch(suggestions, args[0], "reload");
            addIfMatch(suggestions, args[0], "toggle");
            addIfMatch(suggestions, args[0], "mode");
            return suggestions;
        }

        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            addIfMatch(suggestions, args[1], "hearts");
            addIfMatch(suggestions, args[1], "full");
            addIfMatch(suggestions, args[1], "toggle");
        }

        return suggestions;
    }

    private void addIfMatch(List<String> target, String input, String candidate) {
        if (candidate.startsWith(input.toLowerCase())) {
            target.add(candidate);
        }
    }
}

