package dev.kate.erd.core.topology;

import dev.kate.erd.core.model.BlockPos;
import dev.kate.erd.core.model.NetworkId;

import java.util.List;
import java.util.Set;

/**
 * Result of a topology computation operation.
 *
 * <p>This sealed interface hierarchy represents the possible outcomes
 * of topology operations like segment addition or removal. Each variant
 * carries the information needed to apply the change to the network state.
 *
 * <p>Thread-safety: All implementations are immutable records.
 */
public sealed interface TopologyResult {

    /**
     * The network version at which this result was computed.
     * Used for optimistic concurrency control.
     *
     * @return the source version
     */
    long sourceVersion();

    /**
     * No topology change occurred.
     *
     * @param sourceVersion the version at computation time
     */
    record NoChange(long sourceVersion) implements TopologyResult {}

    /**
     * A new isolated network was created for a single segment.
     *
     * @param sourceVersion the version at computation time
     * @param newNetworkId the ID assigned to the new network
     * @param position the segment position
     */
    record NetworkCreated(
            long sourceVersion,
            NetworkId newNetworkId,
            BlockPos position
    ) implements TopologyResult {}

    /**
     * A segment was added to an existing network.
     *
     * @param sourceVersion the version at computation time
     * @param networkId the network the segment joined
     * @param position the new segment position
     */
    record SegmentAdded(
            long sourceVersion,
            NetworkId networkId,
            BlockPos position
    ) implements TopologyResult {}

    /**
     * Multiple networks were merged into one due to a bridging segment.
     *
     * @param sourceVersion the version at computation time
     * @param primaryNetworkId the network that absorbs the others
     * @param mergedNetworkIds networks being merged into primary (not including primary)
     * @param bridgePosition the segment position that caused the merge
     */
    record NetworksMerged(
            long sourceVersion,
            NetworkId primaryNetworkId,
            Set<NetworkId> mergedNetworkIds,
            BlockPos bridgePosition
    ) implements TopologyResult {
        public NetworksMerged {
            mergedNetworkIds = Set.copyOf(mergedNetworkIds);
        }
    }

    /**
     * A segment was removed from a network but the network remains connected.
     *
     * @param sourceVersion the version at computation time
     * @param networkId the network affected
     * @param removedPosition the removed segment position
     */
    record SegmentRemoved(
            long sourceVersion,
            NetworkId networkId,
            BlockPos removedPosition
    ) implements TopologyResult {}

    /**
     * A network was dissolved (last segment removed).
     *
     * @param sourceVersion the version at computation time
     * @param networkId the dissolved network
     * @param removedPosition the last segment position
     */
    record NetworkDissolved(
            long sourceVersion,
            NetworkId networkId,
            BlockPos removedPosition
    ) implements TopologyResult {}

    /**
     * A network was split into multiple components.
     *
     * @param sourceVersion the version at computation time
     * @param originalNetworkId the original network being split
     * @param removedPosition the segment position that was removed
     * @param resultingComponents the new network assignments after split
     */
    record NetworkSplit(
            long sourceVersion,
            NetworkId originalNetworkId,
            BlockPos removedPosition,
            List<SplitComponent> resultingComponents
    ) implements TopologyResult {
        public NetworkSplit {
            resultingComponents = List.copyOf(resultingComponents);
        }
    }

    /**
     * Represents one component resulting from a network split.
     *
     * @param networkId the network ID for this component
     *                  (may be original ID for one component, new IDs for others)
     * @param positions the segment positions in this component
     * @param retainsOriginalId true if this component keeps the original network ID
     */
    record SplitComponent(
            NetworkId networkId,
            Set<BlockPos> positions,
            boolean retainsOriginalId
    ) {
        public SplitComponent {
            positions = Set.copyOf(positions);
        }
    }
}
