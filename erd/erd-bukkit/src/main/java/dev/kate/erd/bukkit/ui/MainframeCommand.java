package dev.kate.erd.bukkit.ui;

import dev.kate.erd.core.dataplane.Binding;
import dev.kate.erd.core.dataplane.DataControlPlane;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.model.ControllerId;
import dev.kate.erd.core.model.MachineId;
import dev.kate.erd.core.model.NetworkId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Command handler for mainframe binding operations.
 *
 * <p>This provides a command-driven UI for:
 * <ul>
 *   <li>Listing machines on a DATA network</li>
 *   <li>Listing controllers on a DATA network</li>
 *   <li>Creating bindings between machines and controllers</li>
 *   <li>Removing bindings</li>
 *   <li>Viewing current bindings</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * /mainframe list machines     - List all machines
 * /mainframe list controllers  - List all controllers
 * /mainframe list bindings     - List all bindings
 * /mainframe bind &lt;machine&gt; &lt;controller&gt; - Create binding
 * /mainframe unbind &lt;machine&gt; &lt;controller&gt; - Remove binding
 * /mainframe status            - Show network status
 * </pre>
 */
public final class MainframeCommand implements CommandExecutor, TabCompleter {

    private final DataControlPlane controlPlane;
    private final InstanceManager instanceManager;

    /**
     * Creates a new mainframe command handler.
     *
     * @param controlPlane the DATA control plane
     * @param instanceManager the instance manager
     */
    public MainframeCommand(DataControlPlane controlPlane, InstanceManager instanceManager) {
        this.controlPlane = controlPlane;
        this.instanceManager = instanceManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "list" -> handleList(sender, args);
            case "bind" -> handleBind(sender, args);
            case "unbind" -> handleUnbind(sender, args);
            case "status" -> handleStatus(sender, args);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand: " + subCommand, NamedTextColor.RED));
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /mainframe list <machines|controllers|bindings>", NamedTextColor.YELLOW));
            return true;
        }

        String type = args[1].toLowerCase();

        return switch (type) {
            case "machines" -> {
                listMachines(sender);
                yield true;
            }
            case "controllers" -> {
                listControllers(sender);
                yield true;
            }
            case "bindings" -> {
                listBindings(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown list type: " + type, NamedTextColor.RED));
                yield true;
            }
        };
    }

    private void listMachines(CommandSender sender) {
        Collection<MachineInstance> machines = instanceManager.allMachines();

        if (machines.isEmpty()) {
            sender.sendMessage(Component.text("No machines registered.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Machines ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (MachineInstance machine : machines) {
            String id = machine.id().id().toString().substring(0, 8);
            String type = machine.definition().typeId();
            String status = machine.status().name();

            // Get DATA network if registered
            Optional<NetworkId> networkOpt = controlPlane.getMachineNetwork(machine.id());
            String networkStr = networkOpt.map(n -> n.id().toString().substring(0, 8)).orElse("none");

            Component line = Component.text()
                .append(Component.text("• ", NamedTextColor.GRAY))
                .append(Component.text(id, NamedTextColor.WHITE))
                .append(Component.text(" [" + type + "] ", NamedTextColor.AQUA))
                .append(Component.text(status, getStatusColor(status)))
                .append(Component.text(" network:", NamedTextColor.GRAY))
                .append(Component.text(networkStr, NamedTextColor.YELLOW))
                .build();

            if (sender instanceof Player) {
                line = line.clickEvent(ClickEvent.suggestCommand("/mainframe bind " + machine.id().id()));
            }

            sender.sendMessage(line);
        }
    }

    private void listControllers(CommandSender sender) {
        Collection<ControllerInstance> controllers = instanceManager.allControllers();

        if (controllers.isEmpty()) {
            sender.sendMessage(Component.text("No controllers registered.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Controllers ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (ControllerInstance controller : controllers) {
            String id = controller.id().id().toString().substring(0, 8);
            String type = controller.definition().typeId();
            String status = controlPlane.getControllerStatus(controller.id()).name();
            boolean isMainframe = controller.definition().isMainframe();

            Component line = Component.text()
                .append(Component.text("• ", NamedTextColor.GRAY))
                .append(Component.text(id, NamedTextColor.WHITE))
                .append(Component.text(" [" + type + "] ", NamedTextColor.AQUA))
                .append(Component.text(status, getStatusColor(status)))
                .append(isMainframe ? Component.text(" [MAINFRAME]", NamedTextColor.LIGHT_PURPLE) : Component.empty())
                .build();

            sender.sendMessage(line);
        }
    }

    private void listBindings(CommandSender sender) {
        // Collect all bindings from all networks
        List<Binding> allBindings = new ArrayList<>();

        for (MachineInstance machine : instanceManager.allMachines()) {
            allBindings.addAll(controlPlane.getBindingsForMachine(machine.id()));
        }

        // Remove duplicates
        Set<String> seen = new HashSet<>();
        allBindings = allBindings.stream()
            .filter(b -> seen.add(b.id().toString()))
            .toList();

        if (allBindings.isEmpty()) {
            sender.sendMessage(Component.text("No bindings.", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== Bindings ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (Binding binding : allBindings) {
            String machineId = binding.machineId().id().toString().substring(0, 8);
            String controllerId = binding.controllerId().id().toString().substring(0, 8);

            Component line = Component.text()
                .append(Component.text("• Machine ", NamedTextColor.GRAY))
                .append(Component.text(machineId, NamedTextColor.GREEN))
                .append(Component.text(" ↔ Controller ", NamedTextColor.GRAY))
                .append(Component.text(controllerId, NamedTextColor.AQUA))
                .build();

            sender.sendMessage(line);
        }
    }

    private boolean handleBind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mainframe bind <machineId> <controllerId>", NamedTextColor.YELLOW));
            return true;
        }

        // Parse machine ID
        MachineId machineId;
        try {
            machineId = MachineId.parse(args[1]);
        } catch (IllegalArgumentException e) {
            // Try partial match
            machineId = findMachineByPartialId(args[1]);
            if (machineId == null) {
                sender.sendMessage(Component.text("Invalid machine ID: " + args[1], NamedTextColor.RED));
                return true;
            }
        }

        // Parse controller ID
        ControllerId controllerId;
        try {
            controllerId = ControllerId.parse(args[2]);
        } catch (IllegalArgumentException e) {
            controllerId = findControllerByPartialId(args[2]);
            if (controllerId == null) {
                sender.sendMessage(Component.text("Invalid controller ID: " + args[2], NamedTextColor.RED));
                return true;
            }
        }

        // Create binding
        var result = controlPlane.createBinding(controllerId, machineId);

        if (result instanceof DataControlPlane.BindingOperationResult.Success success) {
            sender.sendMessage(Component.text("Binding created successfully!", NamedTextColor.GREEN));
        } else if (result instanceof DataControlPlane.BindingOperationResult.Failure failure) {
            sender.sendMessage(Component.text("Failed to create binding: " + failure.reason(), NamedTextColor.RED));
        }

        return true;
    }

    private boolean handleUnbind(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mainframe unbind <machineId> <controllerId>", NamedTextColor.YELLOW));
            return true;
        }

        // Parse IDs
        MachineId machineId = findMachineByPartialId(args[1]);
        ControllerId controllerId = findControllerByPartialId(args[2]);

        if (machineId == null || controllerId == null) {
            sender.sendMessage(Component.text("Invalid machine or controller ID", NamedTextColor.RED));
            return true;
        }

        // Find and remove the binding
        List<Binding> bindings = controlPlane.getBindingsForMachine(machineId);
        Optional<Binding> toRemove = bindings.stream()
            .filter(b -> b.controllerId().equals(controllerId))
            .findFirst();

        if (toRemove.isPresent()) {
            controlPlane.removeBinding(toRemove.get().id());
            sender.sendMessage(Component.text("Binding removed.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No binding found between these entities.", NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("=== ERD Network Status ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        int machineCount = instanceManager.machineCount();
        int controllerCount = instanceManager.controllerCount();
        int mainframeCount = instanceManager.allMainframes().size();

        sender.sendMessage(Component.text("Machines: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(machineCount), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Controllers: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(controllerCount), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Mainframes: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(mainframeCount), NamedTextColor.LIGHT_PURPLE)));

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Mainframe Commands ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/mainframe list machines", NamedTextColor.YELLOW)
            .append(Component.text(" - List all machines", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mainframe list controllers", NamedTextColor.YELLOW)
            .append(Component.text(" - List all controllers", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mainframe list bindings", NamedTextColor.YELLOW)
            .append(Component.text(" - List all bindings", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mainframe bind <machine> <controller>", NamedTextColor.YELLOW)
            .append(Component.text(" - Create binding", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mainframe unbind <machine> <controller>", NamedTextColor.YELLOW)
            .append(Component.text(" - Remove binding", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/mainframe status", NamedTextColor.YELLOW)
            .append(Component.text(" - Show network status", NamedTextColor.GRAY)));
    }

    private NamedTextColor getStatusColor(String status) {
        return switch (status.toUpperCase()) {
            case "RUNNING", "CONNECTED", "BOUND" -> NamedTextColor.GREEN;
            case "IDLE", "UNASSIGNED" -> NamedTextColor.YELLOW;
            case "PAUSED" -> NamedTextColor.AQUA;
            case "BLIND", "NO_SIGNAL" -> NamedTextColor.RED;
            case "ERROR", "INVALID" -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.GRAY;
        };
    }

    private MachineId findMachineByPartialId(String partial) {
        for (MachineInstance machine : instanceManager.allMachines()) {
            if (machine.id().id().toString().startsWith(partial)) {
                return machine.id();
            }
        }
        return null;
    }

    private ControllerId findControllerByPartialId(String partial) {
        for (ControllerInstance controller : instanceManager.allControllers()) {
            if (controller.id().id().toString().startsWith(partial)) {
                return controller.id();
            }
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {

        if (args.length == 1) {
            return filterCompletions(List.of("list", "bind", "unbind", "status", "help"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return filterCompletions(List.of("machines", "controllers", "bindings"), args[1]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("bind") || args[0].equalsIgnoreCase("unbind"))) {
            // Suggest machine IDs
            return instanceManager.allMachines().stream()
                .map(m -> m.id().id().toString().substring(0, 8))
                .filter(id -> id.startsWith(args[1]))
                .toList();
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("bind") || args[0].equalsIgnoreCase("unbind"))) {
            // Suggest controller IDs
            return instanceManager.allControllers().stream()
                .map(c -> c.id().id().toString().substring(0, 8))
                .filter(id -> id.startsWith(args[2]))
                .toList();
        }

        return List.of();
    }

    private List<String> filterCompletions(List<String> options, String prefix) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
            .toList();
    }
}
