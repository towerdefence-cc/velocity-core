package dev.emortal.velocity.friends.commands;

import dev.emortal.api.service.FriendGrpc;
import dev.emortal.api.service.McPlayerGrpc;
import dev.emortal.api.service.McPlayerProto;
import dev.emortal.api.service.PlayerTrackerGrpc;
import dev.emortal.api.utils.GrpcStubCollection;
import dev.emortal.velocity.friends.FriendCache;
import dev.emortal.velocity.general.UsernameSuggestions;
import dev.emortal.velocity.utils.CommandUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class FriendCommand {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendCommand.class);

    private static final Component HELP_MESSAGE = MINI_MESSAGE.deserialize(
            """
                    <light_purple>------ Friend Help ------
                    <click:suggest_command:'/friend add '>/friend add <name></click>
                    <click:suggest_command:'/friend remove '>/friend remove <name></click>
                    <click:suggest_command:'/friend requests '>/friend requests <incoming/outgoing> [page]</click>
                    <click:suggest_command:'/friend purge requests '>/friend purge requests <incoming/outgoing></click>
                    -----------------------"""//todo purge requests
    );

    private final UsernameSuggestions usernameSuggestions;

    private final FriendAddSub friendAddSub;
    private final FriendDenySubs friendDenySubs;
    private final FriendListSub friendListSub;
    private final FriendRemoveSub friendRemoveSub;
    private final FriendRequestPurgeSub friendRequestPurgeSub;
    private final FriendRequestsSub friendRequestsSub;

    public FriendCommand(ProxyServer proxyServer, UsernameSuggestions usernameSuggestions, FriendCache friendCache) {
        this.usernameSuggestions = usernameSuggestions;

        McPlayerGrpc.McPlayerFutureStub mcPlayerService = GrpcStubCollection.getPlayerService().orElse(null);
        FriendGrpc.FriendFutureStub friendService = GrpcStubCollection.getFriendService().orElse(null);
        PlayerTrackerGrpc.PlayerTrackerFutureStub playerTrackerService = GrpcStubCollection.getPlayerTrackerService().orElse(null);

        this.friendAddSub = new FriendAddSub(mcPlayerService, friendService, friendCache);
        this.friendDenySubs = new FriendDenySubs(mcPlayerService, friendService);
        this.friendListSub = new FriendListSub(mcPlayerService, playerTrackerService, friendCache);
        this.friendRemoveSub = new FriendRemoveSub(mcPlayerService, friendService, friendCache);
        this.friendRequestPurgeSub = new FriendRequestPurgeSub(friendService);
        this.friendRequestsSub = new FriendRequestsSub(friendService, mcPlayerService);

        proxyServer.getCommandManager().register(this.createCommand());
    }

    private int executeBase(CommandContext<CommandSource> context) {
        context.getSource().sendMessage(HELP_MESSAGE);
        return 1;
    }

    private BrigadierCommand createCommand() {
        return new BrigadierCommand(
                LiteralArgumentBuilder.<CommandSource>literal("friend")
                        .requires(CommandUtils.isPlayer())
                        .executes(this::executeBase)
                        .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                                .executes(this.friendListSub::execute)
                                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", integer(1))
                                        .executes(this.friendListSub::execute)
                                ))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("requests")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("incoming")
                                        .executes(this.friendRequestsSub::executeIncoming)
                                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", integer(1))
                                                .executes(this.friendRequestsSub::executeIncoming)
                                        ))
                                .then(LiteralArgumentBuilder.<CommandSource>literal("outgoing")
                                        .executes(this.friendRequestsSub::executeOutgoing)
                                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("page", integer(1))
                                                .executes(this.friendRequestsSub::executeOutgoing)
                                        ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("purge")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("requests")
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("incoming")
                                                .executes(this.friendRequestPurgeSub::executeIncoming)
                                        )
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("outgoing")
                                                .executes(this.friendRequestPurgeSub::executeOutgoing)
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", string())
                                        .suggests((context, builder) -> this.usernameSuggestions.command(context, builder, McPlayerProto.McPlayerSearchRequest.FilterMethod.NONE))
                                        .executes(this.friendAddSub::execute)
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", string()) // todo filter by friends
                                        .executes(this.friendRemoveSub::execute)
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("deny")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", string())
                                        .suggests((context, builder) -> this.usernameSuggestions.command(context, builder, McPlayerProto.McPlayerSearchRequest.FilterMethod.NONE))
                                        .executes(this.friendDenySubs::executeDeny)
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSource>literal("revoke")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", string())
                                        .suggests((context, builder) -> this.usernameSuggestions.command(context, builder, McPlayerProto.McPlayerSearchRequest.FilterMethod.NONE))
                                        .executes(this.friendDenySubs::executeRevoke)
                                )
                        )
                        .build()
        );
    }
}
