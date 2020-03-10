/*

 */

package org.ballerinalang.langserver.codeaction.providers;

import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.command.CommandUtil;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.LSContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionKeys;
import org.ballerinalang.langserver.commons.codeaction.CodeActionNodeType;
import org.ballerinalang.langserver.commons.workspace.LSDocumentIdentifier;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentManager;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Code Action provider for automatic data mapping.
 */

@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class AIDataMapperCodeAction extends AbstractCodeActionProvider {

    /**
     * {@inheritDoc}
     */

    @Override
    public List<CodeAction> getCodeActions(CodeActionNodeType nodeType, LSContext lsContext,
                                           List<Diagnostic> diagnostics) {
        List<CodeAction> actions = new ArrayList<>();
        WorkspaceDocumentManager documentManager = lsContext.get(CodeActionKeys.DOCUMENT_MANAGER_KEY);
        Optional<Path> filePath = CommonUtil.getPathFromURI(lsContext.get(CodeActionKeys.FILE_URI_KEY));
        LSDocumentIdentifier document = null;
        try {
            document = documentManager.getLSDocument(filePath.get());
        } catch (WorkspaceDocumentException e) {
            // ignore
        }
        if (document != null) {
            for (Diagnostic diagnostic : diagnostics) {
                // Check if this is a data mapper problem. If so add action
                /*
                Posible errors : INCOMPATIBLE_TYPES, INCOMPATIBLE_TYPE_PATTERN,
                 */

                if (diagnostic.getMessage().toLowerCase(Locale.ROOT).contains(CommandConstants.INCOMPATIBLE_TYPES)) {
                    CodeAction codeAction = CommandUtil.getAIDataMapperCommand(document, diagnostic, lsContext);
                    if (codeAction != null) {
                        actions.add(codeAction);
                    }
                }
                if (diagnostic.getMessage().toLowerCase(Locale.ROOT).contains("missing required parameter")) {
                    CodeAction codeAction = CommandUtil.getAIConnectorMapperCommand(document, diagnostic, lsContext);
                    if (codeAction != null) {
                        actions.add(codeAction);
                    }
                }
            }
        }

        return actions;
    }
}

