package floobits.impl;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import floobits.Listener;
import floobits.common.*;
import floobits.common.interfaces.IContext;
import floobits.common.protocol.FlooUser;
import floobits.dialogs.SelectAccount;
import floobits.dialogs.ShareProjectDialog;
import floobits.utilities.Flog;
import floobits.windows.ChatManager;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * I am the link between a project and floobits
 */
public class ContextImpl extends IContext {

    private Listener listener = new Listener(this);
    public Project project;
    public ChatManager chatManager;

    public ContextImpl(Project project) {
        super();
        this.project = project;
        this.iFactory = new FactoryImpl(this, editor);
    }

    public void statusMessage(String message, NotificationType notificationType) {
        Flog.statusMessage(message, notificationType, project);
    }

    @Override
    public void loadChatManager() {
        chatManager = new ChatManager(this);
    }

    @Override public void flashMessage(final String message) {
        Flog.flashMessage(message, project);
    }


    @Override public void warnMessage(String message) {
        Flog.log(message);
        if (chatManager != null && chatManager.isOpen()) {
            chatManager.statusMessage(message);
        }
        statusMessage(message, NotificationType.WARNING);
    }

    @Override public void statusMessage(String message) {
        Flog.log(message);
        if (chatManager != null && chatManager.isOpen()) {
            chatManager.statusMessage(message);
        }
        statusMessage(message, NotificationType.INFORMATION);
    }

    @Override public void errorMessage(String message) {
        Flog.warn(message);
        statusMessage(message, NotificationType.ERROR);
        if (chatManager != null && chatManager.isOpen()) {
            chatManager.errorMessage(message);
        }
    }

    @Override
    public Object getActualContext() {
        return project;
    }

    @Override
    protected void shareProjectDialog(String name, List<String> orgs, final String host, final boolean _private_, final String projectPath) {
        final ContextImpl context = this;
        ShareProjectDialog shareProjectDialog = new ShareProjectDialog(name, orgs, project, new RunLater<ShareProjectDialog>() {
            @Override
            public void run(ShareProjectDialog dialog) {
                if (API.createWorkspace(host, dialog.getOrgName(), dialog.getWorkspaceName(), context, _private_)) {
                    joinWorkspace(new FlooUrl(host, dialog.getOrgName(), dialog.getWorkspaceName(), Constants.defaultPort, true), projectPath, true);
                }
            }
        });
        shareProjectDialog.createCenterPanel();
        shareProjectDialog.show();
    }

    @Override
    public synchronized void shutdown() {
        super.shutdown();
        if (chatManager != null) {
            chatManager.clearUsers();
        }
        try {
            listener.shutdown();
        } catch (Throwable e) {
            Flog.warn(e);
        }
        listener = new Listener(this);
    }

    @Override
    public void setUsers(Map<Integer, FlooUser> users) {
        if (chatManager == null) {
            return;
        }
        chatManager.setUsers(users);
    }

    public void setListener(boolean b) {
        listener.isListening.set(b);
    }

    @Override
    public void mainThread(Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable);
    }

    @Override
    public void readThread(final Runnable runnable) {
        final ContextImpl context = this;
        mainThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApplicationManager.getApplication().runReadAction(runnable);
                } catch (Throwable throwable) {
                    API.uploadCrash(context, throwable);
                }
            }
        });
    }

    @Override
    public void writeThread(final Runnable runnable) {
        final long l = System.currentTimeMillis();
        final ContextImpl context = this;
        mainThread(new Runnable() {
            @Override
            public void run() {
                CommandProcessor.getInstance().executeCommand(context.project, new Runnable() {
                    @Override
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                long time = System.currentTimeMillis() - l;
                                if (time > 200) {
                                    Flog.log("spent %s getting lock", time);
                                }
                                try {
                                    runnable.run();
                                } catch (Throwable throwable) {
                                    API.uploadCrash(context, throwable);
                                }
                            }
                        });
                    }
                }, "Floobits", null);
            }
        });
    }

    @Override
    protected String selectAccount(String[] keys) {
        SelectAccount selectAccount = new SelectAccount(project, keys);
        selectAccount.show();
        int exitCode = selectAccount.getExitCode();
        if (exitCode != DialogWrapper.OK_EXIT_CODE) {
            return null;
        }
        return selectAccount.getAccount();
    }


    @Override
    public void chat(String username, String msg, Date messageDate) {
        if (chatManager == null) {
            return;
        }
        if (!chatManager.isOpen()) {
            statusMessage(String.format("%s: %s", username, msg));
        }
        chatManager.chatMessage(username, msg, messageDate);
    }

    @Override
    public void openChat() {
        if (chatManager != null && !chatManager.isOpen()) {
            chatManager.openChat();
        }
    }

    @Override
    public void listenToEditor(EditorEventHandler editorEventHandler) {
        listener.start(editorEventHandler);
    }
}
