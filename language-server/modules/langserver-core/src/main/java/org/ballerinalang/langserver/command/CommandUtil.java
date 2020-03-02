/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.ballerinalang.jvm.util.BLangConstants;
import org.ballerinalang.langserver.client.ExtendedLanguageClient;
import org.ballerinalang.langserver.command.executors.AddAllDocumentationExecutor;
import org.ballerinalang.langserver.command.executors.AddDocumentationExecutor;
import org.ballerinalang.langserver.command.executors.ChangeAbstractTypeObjExecutor;
import org.ballerinalang.langserver.command.executors.CreateFunctionExecutor;
import org.ballerinalang.langserver.command.executors.CreateTestExecutor;
import org.ballerinalang.langserver.command.executors.ImportModuleExecutor;
import org.ballerinalang.langserver.command.executors.PullModuleExecutor;
import org.ballerinalang.langserver.common.CommonKeys;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.constants.NodeContextKeys;
import org.ballerinalang.langserver.common.position.PositionTreeVisitor;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.common.utils.FunctionGenerator;
import org.ballerinalang.langserver.commons.LSContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionKeys;
import org.ballerinalang.langserver.commons.workspace.LSDocumentIdentifier;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentManager;
import org.ballerinalang.langserver.compiler.DocumentServiceKeys;
import org.ballerinalang.langserver.compiler.LSCompilerUtil;
import org.ballerinalang.langserver.compiler.LSModuleCompiler;
import org.ballerinalang.langserver.compiler.LSPackageLoader;
import org.ballerinalang.langserver.compiler.common.LSCustomErrorStrategy;
import org.ballerinalang.langserver.compiler.common.LSDocumentIdentifierImpl;
import org.ballerinalang.langserver.compiler.common.modal.BallerinaPackage;
import org.ballerinalang.langserver.compiler.exception.CompilationFailedException;
import org.ballerinalang.langserver.diagnostic.DiagnosticsHelper;
import org.ballerinalang.langserver.util.references.SymbolReferencesModel;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.model.tree.expressions.IndexBasedAccessNode;
import org.ballerinalang.model.types.TypeKind;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.*;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.types.BLangObjectTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangValueType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.util.Flags;
import sun.net.www.http.HttpClient;

import java.io.*;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.ballerinalang.langserver.common.utils.CommonUtil.LINE_SEPARATOR;
import static org.ballerinalang.langserver.common.utils.CommonUtil.createVariableDeclaration;
import static org.ballerinalang.langserver.compiler.LSClientLogger.logError;
import static org.ballerinalang.langserver.compiler.LSCompilerUtil.getUntitledFilePath;
import static org.ballerinalang.langserver.util.references.ReferencesUtil.getReferenceAtCursor;

/**
 * Utilities for the command related operations.
 */
public class CommandUtil {

    private BType variableTypeMappingFrom;

    private CommandUtil() {
    }

    /**
     * Get the command for generate test class.
     *
     * @param topLevelNodeType top level node
     * @param docUri           Document URI
     * @param context          {@link LSContext}
     * @return {@link Command} Test Generation command
     * @throws CompilationFailedException thrown when compilation failed
     */
    public static List<CodeAction> getTestGenerationCommand(String topLevelNodeType, String docUri,
                                                            LSContext context)
            throws CompilationFailedException {
        List<CodeAction> actions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        args.add(new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, docUri));
        Position position = context.get(DocumentServiceKeys.POSITION_KEY).getPosition();
        args.add(new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, "" + position.getLine()));
        args.add(new CommandArgument(CommandConstants.ARG_KEY_NODE_COLUMN, "" + position.getCharacter()));

        boolean isService = CommonKeys.SERVICE_KEYWORD_KEY.equals(topLevelNodeType);
        boolean isFunction = CommonKeys.FUNCTION_KEYWORD_KEY.equals(topLevelNodeType);
        WorkspaceDocumentManager documentManager = context.get(DocumentServiceKeys.DOC_MANAGER_KEY);
        if ((isService || isFunction) && !isTopLevelNode(docUri, documentManager, context, position)) {
            return actions;
        }

        if (isService) {
            CodeAction action = new CodeAction(CommandConstants.CREATE_TEST_SERVICE_TITLE);
            action.setCommand(new Command(CommandConstants.CREATE_TEST_SERVICE_TITLE,
                                          CreateTestExecutor.COMMAND, args));
            actions.add(action);
        } else if (isFunction) {
            CodeAction action = new CodeAction(CommandConstants.CREATE_TEST_FUNC_TITLE);
            action.setCommand(new Command(CommandConstants.CREATE_TEST_FUNC_TITLE,
                                          CreateTestExecutor.COMMAND, args));
            actions.add(action);
        }
        return actions;
    }

    private static boolean isTopLevelNode(String uri, WorkspaceDocumentManager docManager, LSContext context,
                                          Position position)
            throws CompilationFailedException {
        Pair<BLangNode, Object> bLangNode = getBLangNode(position.getLine(), position.getCharacter(), uri, docManager,
                                                         context);

        // Only supported for 'public' functions
        if (bLangNode.getLeft() instanceof BLangFunction &&
                !((BLangFunction) bLangNode.getLeft()).getFlags().contains(Flag.PUBLIC)) {
            return false;
        }

        // Only supported for top-level nodes
        return (bLangNode.getLeft() != null && bLangNode.getLeft().parent instanceof BLangPackage);
    }

    public static Pair<BLangNode, Object> getBLangNode(int line, int column, String uri,
                                                       WorkspaceDocumentManager documentManager, LSContext context)
            throws CompilationFailedException {
        Position position = new Position();
        position.setLine(line);
        position.setCharacter(column + 1);
        context.put(DocumentServiceKeys.FILE_URI_KEY, uri);
        TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
        context.put(DocumentServiceKeys.POSITION_KEY, new TextDocumentPositionParams(identifier, position));
        List<BLangPackage> bLangPackages = LSModuleCompiler.getBLangPackages(context, documentManager,
                                                                             LSCustomErrorStrategy.class, true, false,
                                                                             true);
        context.put(DocumentServiceKeys.BLANG_PACKAGES_CONTEXT_KEY, bLangPackages);
        // Get the current package.
        BLangPackage currentBLangPackage = context.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);
        // Run the position calculator for the current package.
        PositionTreeVisitor positionTreeVisitor = new PositionTreeVisitor(context);
        currentBLangPackage.accept(positionTreeVisitor);
        return new ImmutablePair<>(context.get(NodeContextKeys.NODE_KEY),
                                   context.get(NodeContextKeys.PREVIOUSLY_VISITED_NODE_KEY));
    }

    /**
     * Sends a message to the language server client.
     *
     * @param client      Language Server client
     * @param messageType message type
     * @param message     message
     */
    public static void notifyClient(LanguageClient client, MessageType messageType, String message) {
        client.showMessage(new MessageParams(messageType, message));
    }

    /**
     * Clears diagnostics of the client by sending an text edit event.
     *
     * @param client      Language Server client
     * @param diagHelper  diagnostics helper
     * @param documentUri Current text document URI
     * @param context     {@link LSContext}
     */
    public static void clearDiagnostics(ExtendedLanguageClient client, DiagnosticsHelper diagHelper, String documentUri,
                                        LSContext context) {
        context.put(DocumentServiceKeys.FILE_URI_KEY, documentUri);
        WorkspaceDocumentManager docManager = context.get(DocumentServiceKeys.DOC_MANAGER_KEY);
        try {
            LSDocumentIdentifier lsDocument = new LSDocumentIdentifierImpl(documentUri);
            diagHelper.compileAndSendDiagnostics(client, context, lsDocument, docManager);
        } catch (CompilationFailedException e) {
            String msg = "Computing 'diagnostics' failed!";
            TextDocumentIdentifier identifier = new TextDocumentIdentifier(documentUri);
            logError(msg, e, identifier, (Position) null);
        }
    }

    /**
     * Apply a given single text edit.
     *
     * @param editText   Edit text to be inserted
     * @param range      Line Range to be processed
     * @param identifier Document identifier
     * @param client     Language Client
     * @return {@link ApplyWorkspaceEditParams}     Workspace edit params
     */
    public static ApplyWorkspaceEditParams applySingleTextEdit(String editText, Range range,
                                                               VersionedTextDocumentIdentifier identifier,
                                                               LanguageClient client) {

        ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams();
        TextEdit textEdit = new TextEdit(range, editText);
        TextDocumentEdit textDocumentEdit = new TextDocumentEdit(identifier,
                                                                 Collections.singletonList(textEdit));
        Either<TextDocumentEdit, ResourceOperation> documentChange = Either.forLeft(textDocumentEdit);
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonList(documentChange));
        applyWorkspaceEditParams.setEdit(workspaceEdit);
        if (client != null) {
            client.applyEdit(applyWorkspaceEditParams);
        }
        return applyWorkspaceEditParams;
    }

    /**
     * Apply a workspace edit for the current instance.
     *
     * @param documentChanges List of either document edits or set of resource changes for current session
     * @param client          Language Client
     * @return {@link Object}   workspace edit parameters
     */
    public static Object applyWorkspaceEdit(List<Either<TextDocumentEdit, ResourceOperation>> documentChanges,
                                            LanguageClient client) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(documentChanges);
        ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams(workspaceEdit);
        if (client != null) {
            client.applyEdit(applyWorkspaceEditParams);
        }
        return applyWorkspaceEditParams;
    }

    public static BLangObjectTypeNode getObjectNode(int line, int column, String uri,
                                                    WorkspaceDocumentManager documentManager, LSContext context)
            throws CompilationFailedException {
        Pair<BLangNode, Object> bLangNode = getBLangNode(line, column, uri, documentManager, context);
        if (bLangNode.getLeft() instanceof BLangObjectTypeNode) {
            return (BLangObjectTypeNode) bLangNode.getLeft();
        }
        if (bLangNode.getRight() instanceof BLangObjectTypeNode) {
            return (BLangObjectTypeNode) bLangNode.getRight();
        } else {
            BLangNode parent = bLangNode.getLeft().parent;
            while (parent != null) {
                if (parent instanceof BLangObjectTypeNode) {
                    return (BLangObjectTypeNode) parent;
                }
                parent = parent.parent;
            }
            return null;
        }
    }

    public static CodeAction getDocGenerationCommand(String nodeType, String docUri, int line) {
        CommandArgument nodeTypeArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_TYPE, nodeType);
        CommandArgument docUriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, docUri);
        CommandArgument lineStart = new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, String.valueOf(line));
        List<Object> args = new ArrayList<>(Arrays.asList(nodeTypeArg, docUriArg, lineStart));
        CodeAction action = new CodeAction(CommandConstants.ADD_DOCUMENTATION_TITLE);
        action.setCommand(new Command(CommandConstants.ADD_DOCUMENTATION_TITLE,
                                      AddDocumentationExecutor.COMMAND, args));
        return action;
    }

    public static CodeAction getAllDocGenerationCommand(String docUri) {
        CommandArgument docUriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, docUri);
        List<Object> args = new ArrayList<>(Collections.singletonList(docUriArg));
        CodeAction action = new CodeAction(CommandConstants.ADD_ALL_DOC_TITLE);
        action.setCommand(new Command(CommandConstants.ADD_ALL_DOC_TITLE, AddAllDocumentationExecutor.COMMAND, args));
        return action;
    }

    public static CodeAction getFunctionImportCommand(LSDocumentIdentifier document, Diagnostic diagnostic,
                                                      LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        Position position = diagnostic.getRange().getStart();
        int line = position.getLine();
        int column = position.getCharacter();
        String uri = context.get(CodeActionKeys.FILE_URI_KEY);
        CommandArgument lineArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, "" + line);
        CommandArgument colArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_COLUMN, "" + column);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<Object> args = Arrays.asList(lineArg, colArg, uriArg);
        Matcher matcher = CommandConstants.UNDEFINED_FUNCTION_PATTERN.matcher(diagnosticMessage);
        String functionName = (matcher.find() && matcher.groupCount() > 0) ? matcher.group(1) + "(...)" : "";
        WorkspaceDocumentManager docManager = context.get(CodeActionKeys.DOCUMENT_MANAGER_KEY);
        try {
            BLangInvocation node = getFunctionInvocationNode(line, column, document.getURIString(), docManager,
                                                             context);
            if (node != null && node.pkgAlias.value.isEmpty()) {
                boolean isWithinProject = (node.expr == null);
                if (node.expr != null) {
                    BLangPackage bLangPackage = context.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);
                    List<String> currentModules = document.getProjectModules();
                    PackageID nodePkgId = node.expr.type.tsymbol.pkgID;
                    isWithinProject = bLangPackage.packageID.orgName.equals(nodePkgId.orgName) &&
                            currentModules.contains(nodePkgId.name.value);
                }
                if (isWithinProject) {
                    String commandTitle = CommandConstants.CREATE_FUNCTION_TITLE + functionName;
                    CodeAction action = new CodeAction(commandTitle);
                    action.setKind(CodeActionKind.QuickFix);
                    action.setCommand(new Command(commandTitle, CreateFunctionExecutor.COMMAND, args));
                    action.setDiagnostics(diagnostics);
                    return action;
                }
            }
        } catch (CompilationFailedException e) {
            // ignore
        }
        return null;
    }

    public static BLangInvocation getFunctionInvocationNode(int line, int column, String uri,
                                                            WorkspaceDocumentManager documentManager, LSContext context)
            throws CompilationFailedException {
        Pair<BLangNode, Object> bLangNode = getBLangNode(line, column, uri, documentManager, context);
        if (bLangNode.getLeft() instanceof BLangInvocation) {
            return (BLangInvocation) bLangNode.getLeft();
        } else if (bLangNode.getRight() instanceof BLangInvocation) {
            return (BLangInvocation) bLangNode.getRight();
        } else {
            BLangNode parent = bLangNode.getLeft().parent;
            while (parent != null) {
                if (parent instanceof BLangInvocation) {
                    return (BLangInvocation) parent;
                }
                parent = parent.parent;
            }
            return null;
        }
    }

    public static List<CodeAction> getModuleImportCommand(Diagnostic diagnostic,
                                                          LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        List<CodeAction> actions = new ArrayList<>();
        String uri = context.get(DocumentServiceKeys.FILE_URI_KEY);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        String packageAlias = diagnosticMessage.substring(diagnosticMessage.indexOf("'") + 1,
                                                          diagnosticMessage.lastIndexOf("'"));
        LSDocumentIdentifier sourceDocument = new LSDocumentIdentifierImpl(uri);
        String sourceRoot = LSCompilerUtil.getProjectRoot(sourceDocument.getPath());
        sourceDocument.setProjectRootRoot(sourceRoot);
        List<BallerinaPackage> packagesList = new ArrayList<>();
        Stream.of(LSPackageLoader.getSdkPackages(), LSPackageLoader.getHomeRepoPackages())
                .forEach(packagesList::addAll);
        packagesList.stream()
                .filter(pkgEntry -> {
                    String fullPkgName = pkgEntry.getFullPackageNameAlias();
                    return fullPkgName.endsWith("." + packageAlias) || fullPkgName.endsWith("/" + packageAlias);
                })
                .forEach(pkgEntry -> {
                    String commandTitle = CommandConstants.IMPORT_MODULE_TITLE + " "
                            + pkgEntry.getFullPackageNameAlias();
                    CommandArgument pkgArgument = new CommandArgument(CommandConstants.ARG_KEY_MODULE_NAME,
                                                                      pkgEntry.getFullPackageNameAlias());
                    CodeAction action = new CodeAction(commandTitle);
                    action.setKind(CodeActionKind.QuickFix);
                    action.setCommand(new Command(commandTitle, ImportModuleExecutor.COMMAND,
                                                  new ArrayList<>(Arrays.asList(pkgArgument, uriArg))));
                    action.setDiagnostics(diagnostics);
                    actions.add(action);
                });
        return actions;
    }

    public static CodeAction getUnresolvedModulesCommand(Diagnostic diagnostic, LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        String uri = context.get(DocumentServiceKeys.FILE_URI_KEY);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);

        Matcher matcher = CommandConstants.UNRESOLVED_MODULE_PATTERN.matcher(
                diagnosticMessage.toLowerCase(Locale.ROOT)
        );
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (matcher.find() && matcher.groupCount() > 0) {
            List<Object> args = new ArrayList<>();
            String pkgName = matcher.group(1);
            args.add(new CommandArgument(CommandConstants.ARG_KEY_MODULE_NAME, pkgName));
            args.add(uriArg);
            String commandTitle = CommandConstants.PULL_MOD_TITLE;
            CodeAction action = new CodeAction(commandTitle);
            action.setKind(CodeActionKind.QuickFix);
            action.setCommand(new Command(commandTitle, PullModuleExecutor.COMMAND, args));
            action.setDiagnostics(diagnostics);
            return action;
        }
        return null;
    }

    public static CodeAction getChangeAbstractTypeCommand(Diagnostic diagnostic, LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        Position position = diagnostic.getRange().getStart();
        int line = position.getLine();
        int column = position.getCharacter();
        String uri = context.get(CodeActionKeys.FILE_URI_KEY);
        CommandArgument lineArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, "" + line);
        CommandArgument colArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_COLUMN, "" + column);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        Matcher matcher = CommandConstants.FUNC_IN_ABSTRACT_OBJ_PATTERN.matcher(diagnosticMessage);
        if (matcher.find() && matcher.groupCount() > 1) {
            String objectName = matcher.group(2);
            int colonIndex = objectName.lastIndexOf(":");
            String simpleObjName = (colonIndex > -1) ? objectName.substring(colonIndex + 1) : objectName;
            List<Object> args = Arrays.asList(lineArg, colArg, uriArg);
            String commandTitle = String.format(CommandConstants.MAKE_OBJ_NON_ABSTRACT_TITLE, simpleObjName);

            CodeAction action = new CodeAction(commandTitle);
            action.setKind(CodeActionKind.QuickFix);
            action.setCommand(new Command(commandTitle, ChangeAbstractTypeObjExecutor.COMMAND, args));
            action.setDiagnostics(diagnostics);
            return action;
        }
        return null;
    }

    public static CodeAction getNoImplementationFoundCommand(Diagnostic diagnostic, LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        Position position = diagnostic.getRange().getStart();
        int line = position.getLine();
        int column = position.getCharacter();
        String uri = context.get(CodeActionKeys.FILE_URI_KEY);
        CommandArgument lineArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_LINE, "" + line);
        CommandArgument colArg = new CommandArgument(CommandConstants.ARG_KEY_NODE_COLUMN, "" + column);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        Matcher matcher = CommandConstants.NO_IMPL_FOUND_FOR_FUNCTION_PATTERN.matcher(diagnosticMessage);
        if (matcher.find() && matcher.groupCount() > 1) {
            String objectName = matcher.group(2);
            int colonIndex = objectName.lastIndexOf(":");
            String simpleObjName = (colonIndex > -1) ? objectName.substring(colonIndex + 1) : objectName;
            List<Object> args = Arrays.asList(lineArg, colArg, uriArg);
            String commandTitle = String.format(CommandConstants.MAKE_OBJ_ABSTRACT_TITLE, simpleObjName);

            CodeAction action = new CodeAction(commandTitle);
            action.setKind(CodeActionKind.QuickFix);
            action.setCommand(new Command(commandTitle, ChangeAbstractTypeObjExecutor.COMMAND, args));
            action.setDiagnostics(diagnostics);
            return action;
        }
        return null;
    }

    public static CodeAction getUnresolvedPackageCommand(Diagnostic diagnostic, LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        String uri = context.get(CodeActionKeys.FILE_URI_KEY);
        CommandArgument uriArg = new CommandArgument(CommandConstants.ARG_KEY_DOC_URI, uri);
        List<Diagnostic> diagnostics = new ArrayList<>();

        Matcher matcher = CommandConstants.UNRESOLVED_MODULE_PATTERN.matcher(
                diagnosticMessage.toLowerCase(Locale.ROOT)
        );
        if (matcher.find() && matcher.groupCount() > 0) {
            List<Object> args = new ArrayList<>();
            String pkgName = matcher.group(1);
            args.add(new CommandArgument(CommandConstants.ARG_KEY_MODULE_NAME, pkgName));
            args.add(uriArg);
            String commandTitle = CommandConstants.PULL_MOD_TITLE;
            CodeAction action = new CodeAction(commandTitle);
            action.setKind(CodeActionKind.QuickFix);
            action.setCommand(new Command(commandTitle, PullModuleExecutor.COMMAND, args));
            action.setDiagnostics(diagnostics);
            return action;
        }
        return null;
    }

    public static CodeAction getIncompatibleTypesCommand(LSDocumentIdentifier document, Diagnostic diagnostic,
                                                         LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        Position position = diagnostic.getRange().getStart();
        int line = position.getLine();
        int column = position.getCharacter();
        String uri = context.get(CodeActionKeys.FILE_URI_KEY);
        List<Diagnostic> diagnostics = new ArrayList<>();

        Matcher matcher = CommandConstants.INCOMPATIBLE_TYPE_PATTERN.matcher(diagnosticMessage);
        if (matcher.find() && matcher.groupCount() > 1) {
            String foundType = matcher.group(2);
            WorkspaceDocumentManager documentManager = context.get(DocumentServiceKeys.DOC_MANAGER_KEY);
            try {
                BLangFunction func = CommandUtil.getFunctionNode(line, column, document, documentManager, context);
                if (func != null && !BLangConstants.MAIN_FUNCTION_NAME.equals(func.name.value)) {
                    BLangStatement statement = CommandUtil.getStatementByLocation(func.getBody().getStatements(),
                                                                                  line + 1, column + 1);
                    if (statement instanceof BLangReturn) {
                        // Process full-qualified BType name  eg. ballerina/http:Client and if required; add
                        // auto-import
                        matcher = CommandConstants.FQ_TYPE_PATTERN.matcher(foundType);
                        List<TextEdit> edits = new ArrayList<>();
                        String editText = extractTypeName(context, matcher, foundType, edits);

                        // Process function node
                        Position start;
                        Position end;
                        if (func.returnTypeNode instanceof BLangValueType
                                && TypeKind.NIL.equals(((BLangValueType) func.returnTypeNode).getTypeKind())
                                && func.returnTypeNode.getWS() == null) {
                            // eg. function test() {...}
                            start = new Position(func.returnTypeNode.pos.sLine - 1,
                                                 func.returnTypeNode.pos.eCol - 1);
                            end = new Position(func.returnTypeNode.pos.eLine - 1, func.returnTypeNode.pos.eCol - 1);
                            editText = " returns (" + editText + ")";
                        } else {
                            // eg. function test() returns () {...}
                            start = new Position(func.returnTypeNode.pos.sLine - 1,
                                                 func.returnTypeNode.pos.sCol - 1);
                            end = new Position(func.returnTypeNode.pos.eLine - 1, func.returnTypeNode.pos.eCol - 1);
                        }
                        edits.add(new TextEdit(new Range(start, end), editText));

                        // Add code action
                        String commandTitle = CommandConstants.CHANGE_RETURN_TYPE_TITLE + foundType + "'";
                        CodeAction action = new CodeAction(commandTitle);
                        action.setKind(CodeActionKind.QuickFix);
                        action.setDiagnostics(diagnostics);
                        action.setEdit(new WorkspaceEdit(Collections.singletonList(
                                Either.forLeft(new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null),
                                                                    edits))))
                        );
                        return action;
                    }
                }
            } catch (CompilationFailedException e) {
                // ignore
            }
        }
        return null;
    }

    public static CodeAction getAIDataMapperCommand(LSDocumentIdentifier document, Diagnostic diagnostic,
                                                         LSContext context) {
        /* TODO: Complete the command and code action */
        Position position = diagnostic.getRange().getStart();
        String diagnosedContent = getDiagnosedContent(diagnostic, context, document);

        try {
            Position afterAliasPos = offsetInvocation(diagnosedContent, position);
            SymbolReferencesModel.Reference refAtCursor = getReferenceAtCursor(context, document, afterAliasPos);
            int symbolAtCursorTag = refAtCursor.getSymbol().type.tag;
//            int  = symbolAtCursor.type.tag;

            if (symbolAtCursorTag == 12) { // tag 12 is user defined records or non-primitive types (?)
                String commandTitle = "AI Data Mapper";
                CodeAction action = new CodeAction(commandTitle);

                action.setKind(CodeActionKind.QuickFix);

                String uri = context.get(CodeActionKeys.FILE_URI_KEY);
                List<TextEdit> fEdits = getAIDataMapperCodeActionEdits(document, context,refAtCursor, diagnostic);
                action.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
                        new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), fEdits)))));

                List<Diagnostic> diagnostics = new ArrayList<>();
                action.setDiagnostics(diagnostics);
                return action;
            } else {
                return null;
            }

        } catch (CompilationFailedException | WorkspaceDocumentException e) { // | IOException e) {
            // ignore
        }
        return null;
    }

    private static BLangFunction getFunctionNode(int line, int column, LSDocumentIdentifier document,
                                                 WorkspaceDocumentManager docManager, LSContext context)
            throws CompilationFailedException {
        String uri = document.getURIString();
        Position position = new Position();
        position.setLine(line);
        position.setCharacter(column + 1);
        context.put(DocumentServiceKeys.FILE_URI_KEY, uri);
        TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
        context.put(DocumentServiceKeys.POSITION_KEY, new TextDocumentPositionParams(identifier, position));
        LSModuleCompiler.getBLangPackages(context, docManager, LSCustomErrorStrategy.class, true, false, false);

        // Get the current package.
        BLangPackage currentPackage = context.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);

        // If package is testable package process as tests
        // else process normally
        String relativeFilePath = context.get(DocumentServiceKeys.RELATIVE_FILE_PATH_KEY);
        BLangCompilationUnit compilationUnit;
        if (relativeFilePath.startsWith("tests" + File.separator)) {
            compilationUnit = currentPackage.getTestablePkg().getCompilationUnits().stream().
                    filter(compUnit -> (relativeFilePath).equals(compUnit.getName()))
                    .findFirst().orElse(null);
        } else {
            compilationUnit = currentPackage.getCompilationUnits().stream().
                    filter(compUnit -> relativeFilePath.equals(compUnit.getName())).findFirst().orElse(null);
        }
        if (compilationUnit == null) {
            return null;
        }
        Iterator<TopLevelNode> nodeIterator = compilationUnit.getTopLevelNodes().iterator();
        BLangFunction result = null;
        TopLevelNode next = (nodeIterator.hasNext()) ? nodeIterator.next() : null;
        Function<org.ballerinalang.util.diagnostic.Diagnostic.DiagnosticPosition, Boolean> isWithinPosition =
                diagnosticPosition -> {
                    int sLine = diagnosticPosition.getStartLine();
                    int eLine = diagnosticPosition.getEndLine();
                    int sCol = diagnosticPosition.getStartColumn();
                    int eCol = diagnosticPosition.getEndColumn();
                    return ((line > sLine || (line == sLine && column >= sCol)) &&
                            (line < eLine || (line == eLine && column <= eCol)));
                };
        while (next != null) {
            if (isWithinPosition.apply(next.getPosition())) {
                if (next instanceof BLangFunction) {
                    result = (BLangFunction) next;
                    break;
                } else if (next instanceof BLangTypeDefinition) {
                    BLangTypeDefinition typeDefinition = (BLangTypeDefinition) next;
                    if (typeDefinition.typeNode instanceof BLangObjectTypeNode) {
                        BLangObjectTypeNode typeNode = (BLangObjectTypeNode) typeDefinition.typeNode;
                        for (BLangFunction function : typeNode.functions) {
                            if (isWithinPosition.apply(function.getPosition())) {
                                result = function;
                                break;
                            }
                        }
                    }
                } else if (next instanceof BLangService) {
                    BLangService bLangService = (BLangService) next;
                    for (BLangFunction function : bLangService.resourceFunctions) {
                        if (isWithinPosition.apply(function.getPosition())) {
                            result = function;
                            break;
                        }
                    }
                }
                break;
            }
            next = (nodeIterator.hasNext()) ? nodeIterator.next() : null;
        }
        return result;
    }

    /**
     * Find statement by location.
     *
     * @param statements list of statements
     * @param line       line
     * @param column     column
     * @return {@link BLangStatement} if found, NULL otherwise
     */
    private static BLangStatement getStatementByLocation(List<BLangStatement> statements, int line, int column) {
        BLangStatement node = null;
        for (BLangStatement statement : statements) {
            BLangStatement rv;
            if ((rv = getStatementByLocation(statement, line, column)) != null) {
                return rv;
            }
        }
        return node;
    }

    /**
     * Find statements by location.
     *
     * @param node   lookup {@link BLangNode}
     * @param line   line
     * @param column column
     * @return {@link BLangStatement} if found, NULL otherwise
     */
    private static BLangStatement getStatementByLocation(BLangNode node, int line, int column) {
        try {
            if (checkNodeWithin(node, line, column) && node instanceof BLangStatement) {
                return (BLangStatement) node;
            }
            for (Field field : node.getClass().getDeclaredFields()) {
                Object obj = field.get(node);
                if (obj instanceof BLangBlockStmt) {
                    // Found a block-statement field, drilling down further
                    BLangStatement rv;
                    if ((rv = getStatementByLocation(((BLangBlockStmt) obj).getStatements(), line, column)) != null) {
                        return rv;
                    }
                } else if (obj instanceof BLangStatement) {
                    if (checkNodeWithin((BLangStatement) obj, line, column)) {
                        return (BLangStatement) obj;
                    }
                    // Found a statement field, drilling down further
                    BLangStatement rv;
                    if ((rv = getStatementByLocation((BLangStatement) obj, line, column)) != null) {
                        return rv;
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            return null;
        }
        return null;
    }

    private static boolean checkNodeWithin(BLangNode node, int line, int column) {
        int sLine = node.getPosition().getStartLine();
        int eLine = node.getPosition().getEndLine();
        int sCol = node.getPosition().getStartColumn();
        int eCol = node.getPosition().getEndColumn();
        return (line > sLine || (line == sLine && column >= sCol)) &&
                (line < eLine || (line == eLine && column <= eCol));
    }

    private static String extractTypeName(LSContext context, Matcher matcher, String foundType, List<TextEdit> edits) {
        if (matcher.find() && matcher.groupCount() > 2) {
            String orgName = matcher.group(1);
            String alias = matcher.group(2);
            String typeName = matcher.group(3);
            String pkgId = orgName + "/" + alias;
            PackageID currentPkgId = context.get(
                    DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY).packageID;
            if (pkgId.equals(currentPkgId.toString())) {
                foundType = typeName;
            } else {
                List<BLangImportPackage> currentDocImports = CommonUtil.getCurrentFileImports(context);
                boolean pkgAlreadyImported = currentDocImports.stream()
                        .anyMatch(importPkg -> importPkg.orgName.value.equals(orgName)
                                && importPkg.alias.value.equals(alias));
                if (!pkgAlreadyImported) {
                    edits.addAll(CommonUtil.getAutoImportTextEdits(orgName, alias, context));
                }
                foundType = alias + CommonKeys.PKG_DELIMITER_KEYWORD + typeName;
            }
        }
        return foundType;
    }

    public static CodeAction getTaintedParamCommand(Diagnostic diagnostic, LSContext context) {
        String diagnosticMessage = diagnostic.getMessage();
        List<Diagnostic> diagnostics = new ArrayList<>();
        String uri = context.get(DocumentServiceKeys.FILE_URI_KEY);

        try {
            Matcher matcher = CommandConstants.TAINTED_PARAM_PATTERN.matcher(diagnosticMessage);
            if (matcher.find() && matcher.groupCount() > 0) {
                String param = matcher.group(1);
                String commandTitle = String.format(CommandConstants.MARK_UNTAINTED_TITLE, param);
                CodeAction action = new CodeAction(commandTitle);
                action.setKind(CodeActionKind.QuickFix);
                action.setDiagnostics(diagnostics);
                // Extract specific content range
                Range range = diagnostic.getRange();
                WorkspaceDocumentManager documentManager = context.get(DocumentServiceKeys.DOC_MANAGER_KEY);
                String content = getContentOfRange(documentManager, uri, range);
                // Add `untaint` keyword
                matcher = CommandConstants.NO_CONCAT_PATTERN.matcher(content);
                String editText = matcher.find() ? "<@untained>  " + content : "<@untained> (" + content + ")";
                // Create text-edit
                List<TextEdit> edits = new ArrayList<>();
                edits.add(new TextEdit(range, editText));
                VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, null);
                action.setEdit(new WorkspaceEdit(Collections.singletonList(
                        Either.forLeft(new TextDocumentEdit(identifier, edits)))));
                return action;
            }
        } catch (WorkspaceDocumentException | IOException e) {
            //do nothing
        }
        return null;
    }

    private static String getContentOfRange(WorkspaceDocumentManager documentManager, String uri, Range range)
            throws WorkspaceDocumentException, IOException {
        Optional<Path> filePath = CommonUtil.getPathFromURI(uri);
        if (!filePath.isPresent()) {
            return "";
        }
        Path compilationPath = getUntitledFilePath(filePath.toString()).orElse(filePath.get());
        String fileContent = documentManager.getFileContent(compilationPath);

        BufferedReader reader = new BufferedReader(new StringReader(fileContent));
        StringBuilder capture = new StringBuilder();
        int lineNum = 1;
        int sLine = range.getStart().getLine() + 1;
        int eLine = range.getEnd().getLine() + 1;
        int sChar = range.getStart().getCharacter();
        int eChar = range.getEnd().getCharacter();
        String line;
        while ((line = reader.readLine()) != null && lineNum <= eLine) {
            if (lineNum >= sLine) {
                if (sLine == eLine) {
                    // single line range
                    capture.append(line, sChar, eChar);
                    if (line.length() == eChar) {
                        capture.append(System.lineSeparator());
                    }
                } else if (lineNum == sLine) {
                    // range start line
                    capture.append(line.substring(sChar)).append(System.lineSeparator());
                } else if (lineNum == eLine) {
                    // range end line
                    capture.append(line, 0, eChar);
                    if (line.length() == eChar) {
                        capture.append(System.lineSeparator());
                    }
                } else {
                    // range middle line
                    capture.append(line).append(System.lineSeparator());
                }
            }
            lineNum++;
        }
        return capture.toString();
    }

    public static List<CodeAction> getVariableAssignmentCommand(LSDocumentIdentifier document, Diagnostic diagnostic,
                                                                LSContext context) {
        List<CodeAction> actions = new ArrayList<>();
        String uri = context.get(DocumentServiceKeys.FILE_URI_KEY);
        String diagnosedContent = getDiagnosedContent(diagnostic, context, document);
        List<Diagnostic> diagnostics = new ArrayList<>();
        Position position = diagnostic.getRange().getStart();

        try {
            Position afterAliasPos = offsetInvocation(diagnosedContent, position);
            SymbolReferencesModel.Reference refAtCursor = getReferenceAtCursor(context, document, afterAliasPos);
            BSymbol symbolAtCursor = refAtCursor.getSymbol();

            boolean isInvocation = symbolAtCursor instanceof BInvokableSymbol;
            boolean isRemoteInvocation = (symbolAtCursor.flags & Flags.REMOTE) == Flags.REMOTE;

            boolean hasDefaultInitFunction = false;
            boolean hasCustomInitFunction = false;
            if (refAtCursor.getbLangNode() instanceof BLangInvocation) {
                hasDefaultInitFunction = symbolAtCursor instanceof BObjectTypeSymbol;
                hasCustomInitFunction = symbolAtCursor instanceof BInvokableSymbol &&
                        symbolAtCursor.name.value.endsWith("__init");
            }
            boolean isInitInvocation = hasDefaultInitFunction || hasCustomInitFunction;

            String commandTitle = CommandConstants.CREATE_VARIABLE_TITLE;
            CodeAction action = new CodeAction(commandTitle);
            List<TextEdit> edits = getCreateVariableCodeActionEdits(context, uri, refAtCursor,
                                                                    hasDefaultInitFunction, hasCustomInitFunction);
            action.setKind(CodeActionKind.QuickFix);
            action.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
                    new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), edits)))));
            action.setDiagnostics(diagnostics);
            actions.add(action);

            if (isInvocation || isInitInvocation) {
                BType returnType;
                if (hasDefaultInitFunction) {
                    returnType = symbolAtCursor.type;
                } else if (hasCustomInitFunction) {
                    returnType = symbolAtCursor.owner.type;
                } else {
                    returnType = ((BInvokableSymbol) symbolAtCursor).retType;
                }
                boolean hasError = false;
                if (returnType instanceof BErrorType) {
                    hasError = true;
                } else if (returnType instanceof BUnionType) {
                    BUnionType unionType = (BUnionType) returnType;
                    hasError = unionType.getMemberTypes().stream().anyMatch(s -> s instanceof BErrorType);
                    if (!isRemoteInvocation) {
                        // Add type guard code action
                        commandTitle = String.format(CommandConstants.TYPE_GUARD_TITLE, symbolAtCursor.name);
                        List<TextEdit> tEdits = getTypeGuardCodeActionEdits(context, uri, refAtCursor, unionType);
                        action = new CodeAction(commandTitle);
                        action.setKind(CodeActionKind.QuickFix);
                        action.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
                                new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), tEdits)))));
                        action.setDiagnostics(diagnostics);
                        actions.add(action);
                    }
                }
                // Add ignore return value code action
                if (!hasError) {
                    List<TextEdit> iEdits = getIgnoreCodeActionEdits(refAtCursor);
                    commandTitle = CommandConstants.IGNORE_RETURN_TITLE;
                    action = new CodeAction(commandTitle);
                    action.setKind(CodeActionKind.QuickFix);
                    action.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
                            new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null), iEdits)))));
                    action.setDiagnostics(diagnostics);
                    actions.add(action);
                }
            }
        } catch (CompilationFailedException | WorkspaceDocumentException | IOException e) {
            // ignore
        }
        return actions;
    }

    private static String getDiagnosedContent(Diagnostic diagnostic, LSContext context, LSDocumentIdentifier document) {
        WorkspaceDocumentManager docManager = context.get(CodeActionKeys.DOCUMENT_MANAGER_KEY);
        StringBuilder content = new StringBuilder();
        Position start = diagnostic.getRange().getStart();
        Position end = diagnostic.getRange().getEnd();
        try (BufferedReader reader = new BufferedReader(
                new StringReader(docManager.getFileContent(document.getPath())))) {
            String strLine;
            int count = 0;
            while ((strLine = reader.readLine()) != null) {
                if (count >= start.getLine() && count <= end.getLine()) {
                    if (count == start.getLine()) {
                        content.append(strLine.substring(start.getCharacter()));
                        if (start.getLine() != end.getLine()) {
                            content.append(System.lineSeparator());
                        }
                    } else if (count == end.getLine()) {
                        content.append(strLine.substring(0, end.getCharacter()));
                    } else {
                        content.append(strLine).append(System.lineSeparator());
                    }
                }
                if (count == end.getLine()) {
                    break;
                }
                count++;
            }
        } catch (WorkspaceDocumentException | IOException e) {
            // ignore error
        }
        return content.toString();
    }

    private static Position offsetInvocation(String diagnosedContent, Position position) {
        // Diagnosed message only contains the erroneous part of the line
        // Thus we offset into last
        int leftParenthesisIndex = diagnosedContent.indexOf("(");
        diagnosedContent = (leftParenthesisIndex == -1) ? diagnosedContent
                : diagnosedContent.substring(0, leftParenthesisIndex);
        String quotesRemoved = diagnosedContent
                .replaceAll(".*:", "") // package invocation
                .replaceAll(".*->", "") // action invocation
                .replaceAll(".*\\.", ""); // object access invocation
        int bal = diagnosedContent.length() - quotesRemoved.length();
        if (bal > 0) {
            position.setCharacter(position.getCharacter() + bal + 1);
        }
        return position;
    }

    private static List<TextEdit> getCreateVariableCodeActionEdits(LSContext context, String uri,
                                                                   SymbolReferencesModel.Reference referenceAtCursor,
                                                                   boolean hasDefaultInitFunction,
                                                                   boolean hasCustomInitFunction) {
        BLangNode bLangNode = referenceAtCursor.getbLangNode();
        List<TextEdit> edits = new ArrayList<>();
        CompilerContext compilerContext = context.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY);
        Set<String> nameEntries = CommonUtil.getAllNameEntries(bLangNode, compilerContext);
        String variableName = CommonUtil.generateVariableName(bLangNode, nameEntries);

        PackageID currentPkgId = bLangNode.pos.src.pkgID;
        BiConsumer<String, String> importsAcceptor = (orgName, alias) -> {
            boolean notFound = CommonUtil.getCurrentModuleImports(context).stream().noneMatch(
                    pkg -> (pkg.orgName.value.equals(orgName) && pkg.alias.value.equals(alias))
            );
            if (notFound) {
                String pkgName = orgName + "/" + alias;
                edits.add(addPackage(pkgName, context));
            }
        };
        String variableType;
        if (hasDefaultInitFunction) {
            BType bType = referenceAtCursor.getSymbol().type;
            variableType = FunctionGenerator.generateTypeDefinition(importsAcceptor, currentPkgId, bType);
            variableName = CommonUtil.generateVariableName(bType, nameEntries);
        } else if (hasCustomInitFunction) {
            BType bType = referenceAtCursor.getSymbol().owner.type;
            variableType = FunctionGenerator.generateTypeDefinition(importsAcceptor, currentPkgId, bType);
            variableName = CommonUtil.generateVariableName(bType, nameEntries);
        } else {
            // Recursively find parent, when it is an indexBasedAccessNode
            while (bLangNode.parent instanceof IndexBasedAccessNode) {
                bLangNode = bLangNode.parent;
            }
            variableType = FunctionGenerator.generateTypeDefinition(importsAcceptor, currentPkgId, bLangNode.type);
        }
        // Remove brackets of the unions
        variableType = variableType.replaceAll("^\\((.*)\\)$", "$1");
        String editText = createVariableDeclaration(variableName, variableType);
        Position position = new Position(bLangNode.pos.sLine - 1, bLangNode.pos.sCol - 1);
        edits.add(new TextEdit(new Range(position, position), editText));
        return edits;
    }

    private static TextEdit addPackage(String pkgName, LSContext context) {
        DiagnosticPos pos = null;

        // Filter the imports except the runtime import
        List<BLangImportPackage> imports = CommonUtil.getCurrentModuleImports(context);

        if (!imports.isEmpty()) {
            BLangImportPackage lastImport = CommonUtil.getLastItem(imports);
            pos = lastImport.getPosition();
        }

        int endCol = 0;
        int endLine = pos == null ? 0 : pos.getEndLine();

        String editText = "import " + pkgName + ";\n";
        Range range = new Range(new Position(endLine, endCol), new Position(endLine, endCol));
        return new TextEdit(range, editText);
    }

    private static List<TextEdit> getTypeGuardCodeActionEdits(LSContext context, String uri,
                                                              SymbolReferencesModel.Reference referenceAtCursor,
                                                              BUnionType unionType)
            throws WorkspaceDocumentException, IOException {
        WorkspaceDocumentManager docManager = context.get(CodeActionKeys.DOCUMENT_MANAGER_KEY);
        BLangNode bLangNode = referenceAtCursor.getbLangNode();
        Position startPos = new Position(bLangNode.pos.sLine - 1, bLangNode.pos.sCol - 1);
        Position endPosWithSemiColon = new Position(bLangNode.pos.eLine - 1, bLangNode.pos.eCol);
        Position endPos = new Position(bLangNode.pos.eLine - 1, bLangNode.pos.eCol - 1);
        Range newTextRange = new Range(startPos, endPosWithSemiColon);

        List<TextEdit> edits = new ArrayList<>();
        String spaces = StringUtils.repeat(' ', bLangNode.pos.sCol - 1);
        String padding = LINE_SEPARATOR + LINE_SEPARATOR + spaces;
        String content = getContentOfRange(docManager, uri, new Range(startPos, endPos));
        boolean hasError = unionType.getMemberTypes().stream().anyMatch(s -> s instanceof BErrorType);

        List<BType> members = new ArrayList<>((unionType).getMemberTypes());
        long errorTypesCount = unionType.getMemberTypes().stream().filter(t -> t instanceof BErrorType).count();
        boolean transitiveBinaryUnion = unionType.getMemberTypes().size() - errorTypesCount == 1;
        if (transitiveBinaryUnion) {
            members.removeIf(s -> s instanceof BErrorType);
        }
        // Check is binary union type with error type
        if ((unionType.getMemberTypes().size() == 2 || transitiveBinaryUnion) && hasError) {
            members.forEach(bType -> {
                if (bType instanceof BNilType) {
                    // if (foo() is error) {...}
                    String newText = String.format("if (%s is error) {%s}", content, padding);
                    edits.add(new TextEdit(newTextRange, newText));
                } else {
                    // if (foo() is int) {...} else {...}
                    String type = CommonUtil.getBTypeName(bType, context, true);
                    String newText = String.format("if (%s is %s) {%s} else {%s}",
                                                   content, type, padding, padding);
                    edits.add(new TextEdit(newTextRange, newText));
                }
            });
        } else {
            CompilerContext compilerContext = context.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY);
            Set<String> nameEntries = CommonUtil.getAllNameEntries(bLangNode, compilerContext);
            String varName = CommonUtil.generateVariableName(bLangNode, nameEntries);
            String typeDef = CommonUtil.getBTypeName(unionType, context, true);
            boolean addErrorTypeAtEnd;

            List<BType> tMembers = new ArrayList<>((unionType).getMemberTypes());
            if (errorTypesCount > 1) {
                tMembers.removeIf(s -> s instanceof BErrorType);
                addErrorTypeAtEnd = true;
            } else {
                addErrorTypeAtEnd = false;
            }
            List<String> memberTypes = new ArrayList<>();
            IntStream.range(0, tMembers.size())
                    .forEachOrdered(value -> {
                        BType bType = tMembers.get(value);
                        String bTypeName = CommonUtil.getBTypeName(bType, context, true);
                        boolean isErrorType = bType instanceof BErrorType;
                        if (isErrorType && !addErrorTypeAtEnd) {
                            memberTypes.add(bTypeName);
                        } else if (!isErrorType) {
                            memberTypes.add(bTypeName);
                        }
                    });

            if (addErrorTypeAtEnd) {
                memberTypes.add("error");
            }

            String newText = String.format("%s %s = %s;%s", typeDef, varName, content, LINE_SEPARATOR);
            newText += spaces + IntStream.range(0, memberTypes.size() - 1)
                    .mapToObj(value -> {
                        return String.format("if (%s is %s) {%s}", varName, memberTypes.get(value), padding);
                    })
                    .collect(Collectors.joining(" else "));
            newText += String.format(" else {%s}", padding);
            edits.add(new TextEdit(newTextRange, newText));
        }
        return edits;
    }

    private static List<TextEdit> getIgnoreCodeActionEdits(SymbolReferencesModel.Reference referenceAtCursor) {
        String editText = "_ = ";
        BLangNode bLangNode = referenceAtCursor.getbLangNode();
        Position position = new Position(bLangNode.pos.sLine - 1, bLangNode.pos.sCol - 1);
        List<TextEdit> edits = new ArrayList<>();
        edits.add(new TextEdit(new Range(position, position), editText));
        return edits;

    }

    /**
     * Inner class for the command argument holding argument key and argument value.
     */
    public static class CommandArgument {
        private String argumentK;

        private String argumentV;

        public CommandArgument(String argumentK, String argumentV) {
            this.argumentK = argumentK;
            this.argumentV = argumentV;
        }

        public String getArgumentK() {
            return argumentK;
        }

        public String getArgumentV() {
            return argumentV;
        }

    }

    private static List<TextEdit> getAIDataMapperCodeActionEdits(LSDocumentIdentifier document, LSContext context, SymbolReferencesModel.Reference refAtCursor, Diagnostic diagnostic){
        List<TextEdit> fEdits = new ArrayList<>();

        String diagnosticMessage = diagnostic.getMessage();
        Matcher matcher = CommandConstants.INCOMPATIBLE_TYPE_PATTERN.matcher(diagnosticMessage);

        if (matcher.find() && matcher.groupCount() > 1) {
            String foundTypeLeft = matcher.group(1);  // variable at left side of the equal sign
            String foundTypeRight = matcher.group(2);  // variable at right side of the equal sign

            try {
                // Insert function call in the code where error is found
                BLangNode bLangNode = refAtCursor.getbLangNode();
                Position startPos = new Position(bLangNode.pos.sLine - 1, bLangNode.pos.sCol - 1);
                Position endPosWithSemiColon = new Position(bLangNode.pos.eLine - 1, bLangNode.pos.eCol);
                Range newTextRange = new Range(startPos, endPosWithSemiColon);

                BSymbol symbolAtCursor = refAtCursor.getSymbol();
                String variableAtCursor = symbolAtCursor.name.value;
                String generatedFunctionName = String.format("map%sTo%s(%s);", foundTypeRight, foundTypeLeft, variableAtCursor);

                TextEdit functionNameEdit = new TextEdit(newTextRange, generatedFunctionName);
                fEdits.add(functionNameEdit);

                // Insert function declaration at the bottom of the file
                WorkspaceDocumentManager docManager = context.get(CodeActionKeys.DOCUMENT_MANAGER_KEY);
                String fileContent = docManager.getFileContent(docManager.getAllFilePaths().iterator().next());
                String functionName = String.format("map%sTo%s (%s", foundTypeRight, foundTypeLeft, foundTypeRight);
                int indexOfFunctionName = fileContent.indexOf(functionName);
                if (indexOfFunctionName == -1) {
                    int numberOfLinesInFile = fileContent.split("\n").length;
                    Position startPosOfLastLine = new Position(numberOfLinesInFile + 3, 0);
                    Position endPosOfLastLine = new Position(numberOfLinesInFile + 3, 1);
                    Range newFunctionRange = new Range(startPosOfLastLine, endPosOfLastLine);

                    String generatedRecordMappingFunction = getGeneratedRecordMappingFunction(bLangNode, document, context, diagnostic, symbolAtCursor, docManager, foundTypeLeft, foundTypeRight);
                    TextEdit functionEdit = new TextEdit(newFunctionRange, generatedRecordMappingFunction);
                    fEdits.add(functionEdit);
                }

            } catch (WorkspaceDocumentException e) {
                // ignore
            }
        }
        return fEdits;
    }

    private static String getGeneratedRecordMappingFunction(BLangNode bLangNode, LSDocumentIdentifier document, LSContext context, Diagnostic diagnostic, BSymbol symbolAtCursor, WorkspaceDocumentManager docManager, String foundTypeLeft, String foundTypeRight){
        String generatedRecordMappingFunction = "";
        // Schema 1
        BType variableTypeMappingFrom = symbolAtCursor.type;
        List<BField> rightSchemaFields = ((BRecordType) variableTypeMappingFrom).fields;
        JsonObject rightSchema = (JsonObject) recordToJSON(rightSchemaFields);

        JsonObject rightRecordJSON = new JsonObject();
        rightRecordJSON.addProperty("schema", foundTypeRight);
        rightRecordJSON.addProperty("id", "dummy_id");
        rightRecordJSON.addProperty("type", "object");
        rightRecordJSON.add("properties", rightSchema);

        // Schema 2
        try {
//            Position position = diagnostic.getRange().getStart();
//            String lineContent = docManager.getFileContent(docManager.getAllFilePaths().iterator().next()).split("\n")[position.getLine()];
//            int indexOfFoundTypeLeftInLine = lineContent.lastIndexOf(foundTypeLeft);
//            Position leftTypeVariablePosition = new Position(position.getLine(), indexOfFoundTypeLeftInLine);
//            BType variableTypeMappingTo = getReferenceAtCursor(context, document, leftTypeVariablePosition).getSymbol().type;
//            List<BField> leftSchemaFields = ((BRecordType) variableTypeMappingTo).fields;
            List<BField> leftSchemaFields = ((BRecordType) ((BLangSimpleVarRef) bLangNode).expectedType).fields;
            JsonObject leftSchema = (JsonObject) recordToJSON(leftSchemaFields);

            JsonObject leftRecordJSON = new JsonObject();
            leftRecordJSON.addProperty("schema", foundTypeLeft);
            leftRecordJSON.addProperty("id", "dummy_id");
            leftRecordJSON.addProperty("type", "object");
            leftRecordJSON.add("properties", leftSchema);

            JsonArray schemas = new JsonArray();
            schemas.add(leftRecordJSON);
            schemas.add(rightRecordJSON);

            String schemasToSend = schemas.toString();
            ////////////////
            URL url = new URL ("http://127.0.0.1:5000/uploader");
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);

            OutputStream os = con.getOutputStream();
            os.write(schemasToSend.getBytes("UTF-8"));
            os.close();

            InputStream in = new BufferedInputStream(con.getInputStream());
            String result = org.apache.commons.io.IOUtils.toString(in, "UTF-8");
            generatedRecordMappingFunction = result;

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return generatedRecordMappingFunction;
    }

    private static JsonElement recordToJSON(List<BField> schemaFields){
        JsonObject properties = new JsonObject();
        for (BField attribute: schemaFields){
            JsonObject fieldDetails = new JsonObject();
            fieldDetails.addProperty("id", "dummy_id");
            fieldDetails.addProperty("type", String.valueOf(attribute.type));

            /* TODO: Do we need to go to lower levels? */
//            if (attribute.type.tag == 12){
//                fieldDetails.add("properties", recordToJSON(((BRecordType)attribute.type).fields));
//            }
            properties.add(String.valueOf(attribute.name), fieldDetails);
        }

        return properties;
    }

}
