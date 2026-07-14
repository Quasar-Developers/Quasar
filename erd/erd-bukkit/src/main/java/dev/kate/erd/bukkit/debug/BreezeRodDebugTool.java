package dev.kate.erd.bukkit.debug;

import dev.kate.erd.bukkit.ErdPlugin;
import dev.kate.erd.bukkit.adapter.NetworkResourceTransfer;
import dev.kate.erd.core.controller.ControllerInstance;
import dev.kate.erd.core.debug.DebugSnapshot;
import dev.kate.erd.core.debug.MachineIntrospectable;
import dev.kate.erd.core.endpoint.Endpoint;
import dev.kate.erd.core.endpoint.EndpointRole;
import dev.kate.erd.core.engine.NetworkEngine;
import dev.kate.erd.core.machine.InstanceManager;
import dev.kate.erd.core.machine.MachineInstance;
import dev.kate.erd.core.machine.MachineStatus;
import dev.kate.erd.core.machine.resource.PipeNetworkState;
import dev.kate.erd.core.machine.resource.ResourceConsumer;
import dev.kate.erd.core.machine.resource.ResourceProvider;
import dev.kate.erd.core.machine.resource.ResourceType;
import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.ConnectionType;
import dev.kate.erd.core.model.NetworkId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Breeze Rod Debug Tool - Visual debugging for ERD systems.
 *
 * <p>When holding a Breeze Rod:
 * <ul>
 *   <li>Right-click on a machine to see detailed debug information</li>
 *   <li>Shows all machine-specific data without coordinates</li>
 *   <li>Uses the machine introspection API for extensibility</li>
 * </ul>
 */
public class BreezeRodDebugTool implements Listener {

    private final NetworkEngine engine;
    private final InstanceManager instanceManager;
    private final NetworkResourceTransfer resourceTransfer;

    // Color constants for consistent styling
    private static final TextColor COLOR_HEADER_LINE = TextColor.color(0x5555FF); // Blue
    private static final TextColor COLOR_HEADER_TEXT = TextColor.color(0xFF55FF); // Light purple
    private static final TextColor COLOR_LABEL = NamedTextColor.GRAY;
    private static final TextColor COLOR_VALUE_ID = TextColor.color(0xAA00AA); // Purple
    private static final TextColor COLOR_VALUE_TYPE = NamedTextColor.DARK_AQUA;
    private static final TextColor COLOR_VALUE_NUM = NamedTextColor.GREEN;
    private static final TextColor COLOR_POSITIVE = NamedTextColor.GREEN;
    private static final TextColor COLOR_NEGATIVE = NamedTextColor.RED;
    private static final TextColor COLOR_NEUTRAL = NamedTextColor.YELLOW;
    private static final TextColor COLOR_SECTION = NamedTextColor.AQUA;
    private static final TextColor COLOR_SEPARATOR = NamedTextColor.DARK_GRAY;

    public BreezeRodDebugTool(ErdPlugin plugin) {
        this.engine = plugin.getNetworkEngine();
        this.instanceManager = plugin.getInstanceManager();
        this.resourceTransfer = plugin.getResourceTransfer();
    }

    /**
     * Handle breeze rod interactions.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!isHoldingBreezeRod(player)) return;
        if (!player.hasPermission("erd.debug")) return;

        // Only handle right-click
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK &&
            event.getAction() != Action.RIGHT_CLICK_AIR) return;

        // Cancel the event to prevent any interactions
        event.setCancelled(true);

        // Get the block player is looking at
        Block targetBlock = getTargetBlock(player);
        if (targetBlock == null) {
            player.sendMessage(Component.text("Not looking at any block", NamedTextColor.GRAY));
            return;
        }

        BlockPos pos = toBlockPos(targetBlock);

        // Check for machine first (highest priority)
        Optional<MachineInstance> machineOpt = instanceManager.getMachineAt(pos);
        if (machineOpt.isPresent()) {
            MachineInstance machine = machineOpt.get();
            if (machine instanceof MachineIntrospectable introspectable) {
                showMachineDebugInfo(player, introspectable);
            } else {
                showBasicMachineInfo(player, machine);
            }
            return;
        }

        // Check for controller
        Optional<ControllerInstance> controllerOpt = instanceManager.getControllerAt(pos);
        if (controllerOpt.isPresent()) {
            showControllerInfo(player, controllerOpt.get());
            return;
        }

        // Check for endpoint
        var endpointOpt = instanceManager.getEndpointAt(pos);
        if (endpointOpt.isPresent()) {
            showEndpointInfo(player, endpointOpt.get());
            return;
        }

        // Check for network segment
        for (ConnectionType layer : ConnectionType.values()) {
            Optional<NetworkId> networkOpt = engine.getNetworkAt(layer, pos);
            if (networkOpt.isPresent()) {
                showNetworkInfo(player, layer, networkOpt.get());
                return;
            }
        }

        player.sendMessage(Component.text("No ERD component at this location", NamedTextColor.GRAY));
    }

    /**
     * Shows machine debug information using the introspection API.
     */
    private void showMachineDebugInfo(Player player, MachineIntrospectable machine) {
        // Get debug snapshot from machine
        DebugSnapshot snapshot = machine.createDebugSnapshot();

        // Determine icon based on name (simple heuristic)
        String name = machine.debugDisplayName();
        String icon = "⚙";
        if (name.contains("Reactor")) icon = "☢";
        else if (name.contains("Generator")) icon = "⚡";
        else if (name.contains("Battery") || name.contains("Energy")) icon = "🔋";

        // Header style from DebugVisualizer: ICON + " MACHINE"
        Component header = Component.text(icon + " MACHINE", NamedTextColor.WHITE);

        // Boxed top
        player.sendMessage(header);

        // Type line
        player.sendMessage(Component.text("Type: ", NamedTextColor.GRAY)
            .append(Component.text(name, TextColor.color(85, 255, 255)))); // Cyan

        // Format and send all sections (Formatter handles entries)
        for (Component line : DebugDataFormatter.format(snapshot)) {
            player.sendMessage(line);
        }

        // Play a sound
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
            1.0f, machine.hasCriticalIssues() ? 0.5f : 1.5f);
    }

    /**
     * Shows controller information.
     */
    private void showControllerInfo(Player player, ControllerInstance controller) {
        // Header
        Component headerLine = Component.text()
            .append(Component.text("------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .append(Component.text(" Controller Inspector ", COLOR_HEADER_TEXT))
            .append(Component.text("-------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .build();
        player.sendMessage(headerLine);

        player.sendMessage(Component.text()
            .append(Component.text("🎮 ", NamedTextColor.WHITE))
            .append(Component.text(controller.definition().displayName(), COLOR_VALUE_TYPE))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Status: ", COLOR_LABEL))
            .append(Component.text(controller.status().toString(), COLOR_NEUTRAL))
            .build());

        // Footer
        Component footerLine = Component.text("-----------------------------------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH);
        player.sendMessage(footerLine);
    }

    /**
     * Shows basic machine info for machines that don't implement MachineIntrospectable.
     */
    private void showBasicMachineInfo(Player player, MachineInstance machine) {
        // Header
        Component headerLine = Component.text()
            .append(Component.text("------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .append(Component.text(" Machine Inspector ", COLOR_HEADER_TEXT))
            .append(Component.text("-------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .build();
        player.sendMessage(headerLine);

        // Machine name/type line
        String icon = "⚙";
        String name = machine.definition().displayName();
        if (name.contains("Reactor")) icon = "☢";
        else if (name.contains("Generator")) icon = "⚡";
        else if (name.contains("Battery") || name.contains("Energy")) icon = "🔋";

        player.sendMessage(Component.text()
            .append(Component.text(icon + " ", NamedTextColor.WHITE))
            .append(Component.text(name, COLOR_VALUE_TYPE))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Status: ", COLOR_LABEL))
            .append(Component.text(machine.status().toString(), getStatusColor(machine.status())))
            .build());

        // Info section
        player.sendMessage(Component.text("Info", COLOR_SECTION));
        player.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Component.text("Blocks: ", COLOR_LABEL))
            .append(Component.text(String.valueOf(machine.occupiedPositions().size()), COLOR_VALUE_NUM))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("Endpoints: ", COLOR_LABEL))
            .append(Component.text(String.valueOf(machine.endpoints().size()), COLOR_VALUE_NUM))
            .build());

        // Show resource info if available
        if (machine instanceof ResourceProvider provider) {
            var resources = provider.getAvailableResources();
            if (!resources.isEmpty()) {
                player.sendMessage(Component.text("Providing", COLOR_SECTION));
                for (var entry : resources.entrySet()) {
                    player.sendMessage(Component.text()
                        .append(Component.text("  "))
                        .append(Component.text("▲ ", COLOR_POSITIVE, TextDecoration.BOLD))
                        .append(Component.text(entry.getKey().displayName() + ": ", COLOR_LABEL))
                        .append(Component.text(String.valueOf(entry.getValue()), COLOR_POSITIVE))
                        .build());
                }
            }
        }

        if (machine instanceof ResourceConsumer consumer) {
            var requests = consumer.getResourceRequests();
            if (!requests.isEmpty()) {
                player.sendMessage(Component.text("Requesting", COLOR_SECTION));
                for (var entry : requests.entrySet()) {
                    player.sendMessage(Component.text()
                        .append(Component.text("  "))
                        .append(Component.text("▼ ", COLOR_NEGATIVE, TextDecoration.BOLD))
                        .append(Component.text(entry.getKey().displayName() + ": ", COLOR_LABEL))
                        .append(Component.text(String.valueOf(entry.getValue()), COLOR_NEGATIVE))
                        .build());
                }
            }
        }

        // Footer
        Component footerLine = Component.text("-----------------------------------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH);
        player.sendMessage(footerLine);

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Shows endpoint information in the inspector format.
     */
    private void showEndpointInfo(Player player, Endpoint endpoint) {
        // Header
        Component headerLine = Component.text()
            .append(Component.text("------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .append(Component.text(" Endpoint Inspector ", COLOR_HEADER_TEXT))
            .append(Component.text("-------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .build();
        player.sendMessage(headerLine);

        // Determine icon and role color
        String icon = switch (endpoint.role()) {
            case PROVIDER -> "▲";
            case CONSUMER -> "▼";
            case STORAGE -> "◆";
            case BIDIRECTIONAL -> "⇄";
            case PASSIVE -> "○";
        };
        TextColor roleColor = switch (endpoint.role()) {
            case PROVIDER -> COLOR_POSITIVE;
            case CONSUMER -> COLOR_NEGATIVE;
            case STORAGE -> COLOR_NEUTRAL;
            case BIDIRECTIONAL -> COLOR_SECTION;
            case PASSIVE -> COLOR_LABEL;
        };

        // Info line: Role | Layer | Network
        String networkStatus = endpoint.attachedNetwork()
            .map(netId -> netId.id().toString().substring(0, 8))
            .orElse("Disconnected");
        TextColor networkColor = endpoint.isAttached() ? COLOR_POSITIVE : COLOR_NEGATIVE;

        player.sendMessage(Component.text()
            .append(Component.text(icon + " ", roleColor, TextDecoration.BOLD))
            .append(Component.text(endpoint.role().name(), roleColor))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Layer: ", COLOR_LABEL))
            .append(Component.text(endpoint.layer().name(), COLOR_VALUE_TYPE))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Network: ", COLOR_LABEL))
            .append(Component.text(networkStatus, networkColor))
            .build());

        // Find the machine this endpoint belongs to and show resource info
        showEndpointResourceInfo(player, endpoint);

        // Footer
        Component footerLine = Component.text("-----------------------------------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH);
        player.sendMessage(footerLine);
    }

    /**
     * Shows resource information for an endpoint based on its owning machine.
     */
    private void showEndpointResourceInfo(Player player, Endpoint endpoint) {
        // Find the machine that owns this endpoint by checking adjacent positions
        BlockPos pos = endpoint.position();
        MachineInstance owningMachine = null;

        for (BlockPos adjacent : getAdjacentPositions(pos)) {
            var machineOpt = instanceManager.getMachineAt(adjacent);
            if (machineOpt.isPresent()) {
                // Check if this machine has this endpoint
                MachineInstance machine = machineOpt.get();
                for (Endpoint ep : machine.endpoints()) {
                    if (ep.position().equals(pos)) {
                        owningMachine = machine;
                        break;
                    }
                }
                if (owningMachine != null) break;
            }
        }

        if (owningMachine == null) {
            player.sendMessage(Component.text()
                .append(Component.text("  "))
                .append(Component.text("No connected machine", COLOR_LABEL))
                .build());
            return;
        }

        // Show resource info based on endpoint role and type
        if (endpoint.role() == EndpointRole.PROVIDER || endpoint.role() == EndpointRole.STORAGE || endpoint.role() == EndpointRole.BIDIRECTIONAL) {
            if (owningMachine instanceof ResourceProvider provider) {
                var resources = provider.getAvailableResources();
                if (!resources.isEmpty()) {
                    player.sendMessage(Component.text("Providing", COLOR_SECTION));
                    for (var entry : resources.entrySet()) {
                        // Filter by type if PIPE
                        if (endpoint.layer() == ConnectionType.PIPE || endpoint.layer() == ConnectionType.POWER) {
                            player.sendMessage(Component.text()
                                .append(Component.text("  "))
                                .append(Component.text("▲ ", COLOR_POSITIVE, TextDecoration.BOLD))
                                .append(Component.text(entry.getKey().displayName() + ": ", COLOR_LABEL))
                                .append(Component.text(String.valueOf(entry.getValue()), COLOR_POSITIVE))
                                .build());
                        }
                    }
                }
            }
        }

        if (endpoint.role() == EndpointRole.CONSUMER || endpoint.role() == EndpointRole.STORAGE || endpoint.role() == EndpointRole.BIDIRECTIONAL) {
            if (owningMachine instanceof ResourceConsumer consumer) {
                var requests = consumer.getResourceRequests();
                if (!requests.isEmpty()) {
                    player.sendMessage(Component.text("Requesting", COLOR_SECTION));
                    for (var entry : requests.entrySet()) {
                        // Filter by type if PIPE
                        if (endpoint.layer() == ConnectionType.PIPE || endpoint.layer() == ConnectionType.POWER) {
                            player.sendMessage(Component.text()
                                .append(Component.text("  "))
                                .append(Component.text("▼ ", COLOR_NEGATIVE, TextDecoration.BOLD))
                                .append(Component.text(entry.getKey().displayName() + ": ", COLOR_LABEL))
                                .append(Component.text(String.valueOf(entry.getValue()), COLOR_NEGATIVE))
                                .build());
                        }
                    }
                }
            }
        }
    }

    /**
     * Get status color based on machine status.
     */
    private TextColor getStatusColor(MachineStatus status) {
        return switch (status) {
            case RUNNING -> COLOR_POSITIVE;
            case IDLE -> COLOR_NEUTRAL;
            case ERROR, INVALID -> COLOR_NEGATIVE;
            case PAUSED, BLIND -> COLOR_NEUTRAL;
        };
    }

    /**
     * Shows network information in the new Network Inspector format.
     */
    private void showNetworkInfo(Player player, ConnectionType layer, NetworkId networkId) {
        Set<BlockPos> positions = engine.getNetworkSegments(layer, networkId);
        String shortId = networkId.id().toString().substring(0, 8);

        // Count connected providers and consumers
        Set<MachineInstance> connectedMachines = findConnectedMachines(positions);
        int providerCount = 0;
        int consumerCount = 0;

        for (MachineInstance machine : connectedMachines) {
            if (machine instanceof ResourceProvider) providerCount++;
            if (machine instanceof ResourceConsumer) consumerCount++;
        }

        // Header line
        Component headerLine = Component.text()
            .append(Component.text("------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .append(Component.text(" Network Inspector ", COLOR_HEADER_TEXT))
            .append(Component.text("-------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH))
            .build();
        player.sendMessage(headerLine);

        // Info line: ID | Type | Blocks | ▲ providers ▼ consumers
        String networkType = layer == ConnectionType.POWER ? "Segment" : (layer == ConnectionType.PIPE ? "Pipe" : "Data");
        Component infoLine = Component.text()
            .append(Component.text("ID: ", COLOR_LABEL))
            .append(Component.text(shortId, COLOR_VALUE_ID))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Type: ", COLOR_LABEL))
            .append(Component.text(networkType, COLOR_VALUE_TYPE))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("  Blocks: ", COLOR_LABEL))
            .append(Component.text(String.valueOf(positions.size()), COLOR_VALUE_NUM))
            .append(Component.text("   ", COLOR_SEPARATOR))
            .append(Component.text("|", COLOR_SEPARATOR))
            .append(Component.text("   ", COLOR_SEPARATOR))
            .append(Component.text("▲ ", COLOR_POSITIVE, TextDecoration.BOLD))
            .append(Component.text(String.valueOf(providerCount), COLOR_POSITIVE))
            .append(Component.text("   ", COLOR_SEPARATOR))
            .append(Component.text("▼ ", COLOR_NEGATIVE, TextDecoration.BOLD))
            .append(Component.text(String.valueOf(consumerCount), COLOR_NEGATIVE))
            .build();
        player.sendMessage(infoLine);

        // Show type-specific details
        if (layer == ConnectionType.POWER) {
            showPowerNetworkDetails(player, networkId, positions, connectedMachines);
        } else if (layer == ConnectionType.PIPE) {
            showPipeNetworkDetails(player, networkId, positions, connectedMachines);
        } else {
            showDataNetworkDetails(player, networkId, positions, connectedMachines);
        }

        // Footer line
        Component footerLine = Component.text("-----------------------------------------", COLOR_HEADER_LINE, TextDecoration.STRIKETHROUGH);
        player.sendMessage(footerLine);
    }

    /**
     * Show POWER network specific details (electrical metrics).
     */
    private void showPowerNetworkDetails(Player player, NetworkId networkId, Set<BlockPos> positions, Set<MachineInstance> machines) {
        // Calculate power metrics from connected machines
        double totalGeneration = 0;
        double totalLoad = 0;

        for (MachineInstance machine : machines) {
            if (machine instanceof ResourceProvider provider) {
                // Estimate power generation from energy resources
                var resources = provider.getAvailableResources();
                if (resources.containsKey(ResourceType.ENERGY)) {
                    totalGeneration += resources.get(ResourceType.ENERGY) * 0.05; // Convert to kW estimate
                }
            }
            if (machine instanceof ResourceConsumer consumer) {
                var requests = consumer.getResourceRequests();
                if (requests.containsKey(ResourceType.ENERGY)) {
                    totalLoad += requests.get(ResourceType.ENERGY) * 0.05; // Convert to kW estimate
                }
            }
        }

        double delta = totalGeneration - totalLoad;

        // Power section
        player.sendMessage(Component.text("Power", COLOR_SECTION));
        Component powerLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("+ Gen: ", COLOR_POSITIVE))
            .append(Component.text(formatPower(totalGeneration), COLOR_POSITIVE))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("- Load: ", COLOR_NEGATIVE))
            .append(Component.text(formatPower(totalLoad), COLOR_NEGATIVE))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("Δ ", COLOR_NEUTRAL))
            .append(Component.text(formatPower(delta), delta >= 0 ? COLOR_POSITIVE : COLOR_NEGATIVE))
            .build();
        player.sendMessage(powerLine);

        // Electrical section (simulated values based on network size and load)
        double voltage = 380.0; // Base voltage
        double current = totalLoad > 0 ? (totalLoad * 1000 / voltage) : 0; // I = P/V
        double currentLimit = positions.size() * 0.5; // 0.5A capacity per segment
        double heatPercent = currentLimit > 0 ? Math.min(100, (current / currentLimit) * 100) : 0;
        double loss = current * current * 0.001 * positions.size(); // I²R loss estimate

        player.sendMessage(Component.text("Electrical", COLOR_SECTION));
        Component electricalLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("V: ", COLOR_LABEL))
            .append(Component.text(String.format("%.0fV", voltage), COLOR_NEUTRAL))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("I: ", COLOR_LABEL))
            .append(Component.text(String.format("%.1fA", current), COLOR_NEUTRAL))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("Limit: ", COLOR_LABEL))
            .append(Component.text(String.format("%.1fA", currentLimit), COLOR_NEUTRAL))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("Heat: ", COLOR_LABEL))
            .append(Component.text(String.format("%.0f%%", heatPercent), getHeatColor(heatPercent)))
            .append(Component.text("  ", COLOR_SEPARATOR))
            .append(Component.text("Loss: ", COLOR_LABEL))
            .append(Component.text(formatPower(loss / 1000), COLOR_NEUTRAL))
            .build();
        player.sendMessage(electricalLine);

        // Forecast section
        player.sendMessage(Component.text("Forecast", COLOR_SECTION));
        String forecastStatus;
        TextColor forecastColor;
        String forecastMessage;

        if (delta >= 0 && heatPercent < 80) {
            forecastStatus = "Stable";
            forecastColor = COLOR_POSITIVE;
            forecastMessage = " - No brownout expected";
        } else if (delta < 0) {
            forecastStatus = "Deficit";
            forecastColor = COLOR_NEGATIVE;
            forecastMessage = " - Power shortage detected";
        } else {
            forecastStatus = "Warning";
            forecastColor = COLOR_NEUTRAL;
            forecastMessage = " - High heat load";
        }

        Component forecastLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text(forecastStatus, forecastColor))
            .append(Component.text(forecastMessage, COLOR_SEPARATOR))
            .build();
        player.sendMessage(forecastLine);
    }

    /**
     * Show PIPE network specific details (throughput and balance).
     */
    private void showPipeNetworkDetails(Player player, NetworkId networkId, Set<BlockPos> positions, Set<MachineInstance> machines) {
        // Get actual pipe network state if available
        Optional<PipeNetworkState> stateOpt = resourceTransfer.getNetworkState(networkId);

        double inRate = 0;
        double outRate = 0;
        int stored = 0;
        int capacity = positions.size() * PipeNetworkState.BUFFER_PER_SEGMENT;

        if (stateOpt.isPresent()) {
            PipeNetworkState state = stateOpt.get();
            stored = state.getStoredAmount();
            capacity = state.getCapacity();

            // Estimate rates from providers/consumers
            for (var providerInfo : state.getProviders()) {
                inRate += providerInfo.available() * 0.1;
            }
            for (var consumerInfo : state.getConsumers()) {
                ResourceType lockedType = state.getLockedResourceType();
                if (lockedType != null) {
                    outRate += consumerInfo.getRequest(lockedType) * 0.1;
                }
            }
        } else {
            // Fallback: estimate from connected machines
            for (MachineInstance machine : machines) {
                if (machine instanceof ResourceProvider provider) {
                    inRate += provider.getAvailableResources().values().stream()
                        .mapToInt(Integer::intValue).sum() * 0.1;
                }
                if (machine instanceof ResourceConsumer consumer) {
                    outRate += consumer.getResourceRequests().values().stream()
                        .mapToInt(Integer::intValue).sum() * 0.1;
                }
            }
        }

        double delta = inRate - outRate;

        // Throughput section
        player.sendMessage(Component.text("Throughput", COLOR_SECTION));
        Component throughputLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("▲ In : ", COLOR_POSITIVE))
            .append(Component.text(String.format("%.1f/t", inRate), COLOR_POSITIVE))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("▼ Out: ", COLOR_NEGATIVE))
            .append(Component.text(String.format("%.1f/t", outRate), COLOR_NEGATIVE))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("Δ ", COLOR_NEUTRAL))
            .append(Component.text(String.format("%.1f/t", delta), delta >= 0 ? COLOR_POSITIVE : COLOR_NEGATIVE))
            .build();
        player.sendMessage(throughputLine);

        // Balance section
        double fillPercent = capacity > 0 ? (stored * 100.0 / capacity) : 0;

        player.sendMessage(Component.text("Balance", COLOR_SECTION));
        Component balanceLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("Stored: ", COLOR_LABEL))
            .append(Component.text(formatFluid(stored), COLOR_NEUTRAL))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("Capacity: ", COLOR_LABEL))
            .append(Component.text(formatFluid(capacity), COLOR_NEUTRAL))
            .append(Component.text("    ", COLOR_SEPARATOR))
            .append(Component.text("Fill: ", COLOR_LABEL))
            .append(Component.text(String.format("%.0f%%", fillPercent), getFillColor(fillPercent)))
            .build();
        player.sendMessage(balanceLine);

        // Forecast section
        player.sendMessage(Component.text("Forecast", COLOR_SECTION));
        String forecastStatus;
        TextColor forecastColor;
        String forecastMessage;

        if (delta >= 0 || stored > capacity * 0.25) {
            forecastStatus = "Stable";
            forecastColor = COLOR_POSITIVE;
            forecastMessage = " - No depletion expected";
        } else if (stored > 0) {
            // Estimate time to depletion
            double ticksToEmpty = stored / Math.abs(delta);
            double secondsToEmpty = ticksToEmpty / 20.0;
            forecastStatus = "Draining";
            forecastColor = COLOR_NEUTRAL;
            forecastMessage = String.format(" - Empty in %.0fs", secondsToEmpty);
        } else {
            forecastStatus = "Empty";
            forecastColor = COLOR_NEGATIVE;
            forecastMessage = " - Network depleted";
        }

        Component forecastLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text(forecastStatus, forecastColor))
            .append(Component.text(forecastMessage, COLOR_SEPARATOR))
            .build();
        player.sendMessage(forecastLine);
    }

    /**
     * Show DATA network specific details.
     */
    private void showDataNetworkDetails(Player player, NetworkId networkId, Set<BlockPos> positions, Set<MachineInstance> machines) {
        // DATA networks are simpler - just show connectivity
        player.sendMessage(Component.text("Data Network", COLOR_SECTION));
        Component dataLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("Connected devices: ", COLOR_LABEL))
            .append(Component.text(String.valueOf(machines.size()), COLOR_VALUE_NUM))
            .build();
        player.sendMessage(dataLine);

        player.sendMessage(Component.text("Forecast", COLOR_SECTION));
        Component forecastLine = Component.text()
            .append(Component.text("  "))
            .append(Component.text("Stable", COLOR_POSITIVE))
            .append(Component.text(" - Network operational", COLOR_SEPARATOR))
            .build();
        player.sendMessage(forecastLine);
    }

    /**
     * Find all machines connected to the network segment positions.
     */
    private Set<MachineInstance> findConnectedMachines(Set<BlockPos> cablePositions) {
        Set<MachineInstance> machines = new HashSet<>();
        for (BlockPos cablePos : cablePositions) {
            for (BlockPos adjacent : getAdjacentPositions(cablePos)) {
                instanceManager.getMachineAt(adjacent).ifPresent(machines::add);
            }
        }
        return machines;
    }

    /**
     * Format power value (in kW).
     */
    private String formatPower(double kw) {
        if (Math.abs(kw) >= 1000) {
            return String.format("%.1f MW", kw / 1000);
        }
        return String.format("%.1f kW", kw);
    }

    /**
     * Format fluid value (in mb).
     */
    private String formatFluid(int mb) {
        if (mb >= 1000) {
            return String.format("%.1fB", mb / 1000.0); // Buckets
        }
        return String.format("%dmb", mb);
    }

    /**
     * Get color based on heat percentage.
     */
    private TextColor getHeatColor(double percent) {
        if (percent < 50) return COLOR_POSITIVE;
        if (percent < 80) return COLOR_NEUTRAL;
        return COLOR_NEGATIVE;
    }

    /**
     * Get color based on fill percentage.
     */
    private TextColor getFillColor(double percent) {
        if (percent < 25) return COLOR_NEGATIVE;
        if (percent < 50) return COLOR_NEUTRAL;
        return COLOR_POSITIVE;
    }

    private List<BlockPos> getAdjacentPositions(BlockPos pos) {
        return List.of(
            new BlockPos(pos.worldId(), pos.x() + 1, pos.y(), pos.z()),
            new BlockPos(pos.worldId(), pos.x() - 1, pos.y(), pos.z()),
            new BlockPos(pos.worldId(), pos.x(), pos.y() + 1, pos.z()),
            new BlockPos(pos.worldId(), pos.x(), pos.y() - 1, pos.z()),
            new BlockPos(pos.worldId(), pos.x(), pos.y(), pos.z() + 1),
            new BlockPos(pos.worldId(), pos.x(), pos.y(), pos.z() - 1)
        );
    }

    /**
     * Check if player is holding a breeze rod.
     */
    private boolean isHoldingBreezeRod(Player player) {
        var item = player.getInventory().getItemInMainHand();
        return item.getType() == Material.BREEZE_ROD;
    }

    /**
     * Get the block the player is looking at.
     */
    private Block getTargetBlock(Player player) {
        RayTraceResult result = player.rayTraceBlocks(50);
        return result != null ? result.getHitBlock() : null;
    }

    /**
     * Convert Bukkit block to core BlockPos.
     */
    private BlockPos toBlockPos(Block block) {
        return new BlockPos(
            block.getWorld().getUID(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
    }
}
