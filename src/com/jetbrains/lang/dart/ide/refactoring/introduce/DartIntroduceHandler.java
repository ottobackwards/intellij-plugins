package com.jetbrains.lang.dart.ide.refactoring.introduce;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.DartComponentType;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.*;
import com.jetbrains.lang.dart.util.DartElementGenerator;
import com.jetbrains.lang.dart.util.DartNameSuggesterUtil;
import com.jetbrains.lang.dart.util.DartRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author: Fedor.Korotkov
 */
@SuppressWarnings("MethodMayBeStatic")
public abstract class DartIntroduceHandler implements RefactoringActionHandler {
  @Nullable
  protected static PsiElement findAnchor(PsiElement occurrence) {
    return findAnchor(Arrays.asList(occurrence));
  }

  @Nullable
  protected static PsiElement findAnchor(List<PsiElement> occurrences) {
    PsiElement anchor = occurrences.get(0);
    next:
    do {
      final DartStatements statements = PsiTreeUtil.getParentOfType(anchor, DartStatements.class);

      int minOffset = Integer.MAX_VALUE;
      for (PsiElement element : occurrences) {
        minOffset = Math.min(minOffset, element.getTextOffset());
        if (!PsiTreeUtil.isAncestor(statements, element, true)) {
          anchor = statements;
          continue next;
        }
      }

      if (statements == null) {
        return null;
      }

      PsiElement child = null;
      PsiElement[] children = statements.getChildren();
      for (PsiElement aChildren : children) {
        child = aChildren;
        if (child.getTextRange().contains(minOffset)) {
          break;
        }
      }

      return child;
    }
    while (true);
  }

  protected final String myDialogTitle;

  public DartIntroduceHandler(@NotNull final String dialogTitle) {
    myDialogTitle = dialogTitle;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(new DartIntroduceOperation(project, editor, file, null));
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }

  public void performAction(DartIntroduceOperation operation) {
    final PsiFile file = operation.getFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(operation.getEditor());
      if (templateState != null && !templateState.isFinished()) {
        return;
      }
    }

    PsiElement element1 = null;
    PsiElement element2 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
      if (element1 instanceof PsiWhiteSpace) {
        int startOffset = element1.getTextRange().getEndOffset();
        element1 = file.findElementAt(startOffset);
      }
      if (element2 instanceof PsiWhiteSpace) {
        int endOffset = element2.getTextRange().getStartOffset();
        element2 = file.findElementAt(endOffset - 1);
      }
    }
    else {
      if (smartIntroduce(operation)) {
        return;
      }
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    final Project project = operation.getProject();
    if (element1 == null || element2 == null) {
      showCannotPerformError(project, editor);
      return;
    }

    element1 = DartRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    if (element1 == null) {
      showCannotPerformError(project, editor);
      return;
    }

    if (!checkIntroduceContext(file, editor, element1)) {
      return;
    }
    operation.setElement(element1);
    performActionOnElement(operation);
  }

  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (!isValidIntroduceContext(element)) {
      showCannotPerformError(file.getProject(), editor);
      return false;
    }
    return true;
  }

  private void showCannotPerformError(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(
      project,
      editor,
      DartBundle.message("refactoring.introduce.selection.error"),
      myDialogTitle,
      "refactoring.extractMethod"
    );
  }

  protected boolean isValidIntroduceContext(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, DartFormalParameterList.class) == null;
  }

  private boolean smartIntroduce(final DartIntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    final PsiFile file = operation.getFile();
    int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);
    if (!checkIntroduceContext(file, editor, elementAtCaret)) return true;
    final List<DartExpression> expressions = new ArrayList<DartExpression>();
    while (elementAtCaret != null) {
      if (elementAtCaret instanceof DartFile) {
        break;
      }
      if (elementAtCaret instanceof DartExpression) {
        expressions.add((DartExpression)elementAtCaret);
      }
      elementAtCaret = elementAtCaret.getParent();
    }
    if (expressions.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      operation.setElement(expressions.get(0));
      performActionOnElement(operation);
      return true;
    }
    else if (expressions.size() > 1) {
      IntroduceTargetChooser.showChooser(
        editor,
        expressions,
        new Pass<DartExpression>() {
          @Override
          public void pass(DartExpression expression) {
            operation.setElement(expression);
            performActionOnElement(operation);
          }
        }, new Function<DartExpression, String>() {
          public String fun(DartExpression expression) {
            return expression.getText();
          }
        }
      );
      return true;
    }
    return false;
  }

  private void performActionOnElement(DartIntroduceOperation operation) {
    if (!checkEnabled(operation)) {
      return;
    }
    final PsiElement element = operation.getElement();

    final DartExpression initializer = (DartExpression)element;
    operation.setInitializer(initializer);

    operation.setOccurrences(getOccurrences(element, initializer));
    operation.setSuggestedNames(DartNameSuggesterUtil.getSuggestedNames(initializer));
    if (operation.getOccurrences().size() == 0) {
      operation.setReplaceAll(false);
    }

    performActionOnElementOccurrences(operation);
  }

  protected void performActionOnElementOccurrences(final DartIntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled()) {
      ensureName(operation);
      if (operation.isReplaceAll() != null) {
        performInplaceIntroduce(operation);
      }
      else {
        OccurrencesChooser.simpleChooser(editor).showChooser(
          operation.getElement(),
          operation.getOccurrences(),
          new Pass<OccurrencesChooser.ReplaceChoice>() {
            @Override
            public void pass(OccurrencesChooser.ReplaceChoice replaceChoice) {
              operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
              performInplaceIntroduce(operation);
            }
          });
      }
    }
    else {
      performIntroduceWithDialog(operation);
    }
  }

  protected boolean checkEnabled(DartIntroduceOperation operation) {
    return true;
  }

  protected static void ensureName(DartIntroduceOperation operation) {
    if (operation.getName() == null) {
      final Collection<String> suggestedNames = operation.getSuggestedNames();
      if (suggestedNames.size() > 0) {
        operation.setName(suggestedNames.iterator().next());
      }
      else {
        operation.setName("x");
      }
    }
  }


  protected List<PsiElement> getOccurrences(PsiElement element, @NotNull final DartExpression expression) {
    PsiElement context = element;
    DartComponentType type = null;
    do {
      context = PsiTreeUtil.getParentOfType(context, DartComponent.class, true);
      type = DartComponentType.typeOf(context);
    }
    while (type != null && notFunctionMethodClass(type));
    if (context == null) {
      context = expression.getContainingFile();
    }
    return DartRefactoringUtil.getOccurrences(expression, context);
  }

  private static boolean notFunctionMethodClass(DartComponentType type) {
    final boolean isFunctionMethodClass = type == DartComponentType.METHOD ||
                                          type == DartComponentType.FUNCTION ||
                                          type == DartComponentType.INTERFACE ||
                                          type == DartComponentType.CLASS;
    return !isFunctionMethodClass;
  }

  protected void performIntroduceWithDialog(DartIntroduceOperation operation) {
    final Project project = operation.getProject();
    if (operation.getName() == null) {
      DartIntroduceDialog dialog = new DartIntroduceDialog(project, myDialogTitle, operation);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      operation.setName(dialog.getName());
      operation.setReplaceAll(dialog.doReplaceAllOccurrences());
    }

    PsiElement declaration = performRefactoring(operation);
    if (declaration == null) {
      return;
    }
    final Editor editor = operation.getEditor();
    editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
    editor.getSelectionModel().removeSelection();
  }

  protected void performInplaceIntroduce(DartIntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    final DartComponent target = PsiTreeUtil.findChildOfType(statement, DartComponent.class);
    if (target == null) {
      return;
    }
    final List<PsiElement> occurrences = operation.getOccurrences();
    final PsiElement occurrence = DartRefactoringUtil.findOccurrenceUnderCaret(occurrences, operation.getEditor());
    final PsiElement elementForCaret = occurrence != null ? occurrence : target;
    operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
    final InplaceVariableIntroducer<PsiElement> introducer =
      new DartInplaceVariableIntroducer(target.getComponentName(), operation, occurrences);
    introducer.performInplaceRefactoring(new LinkedHashSet<String>(operation.getSuggestedNames()));
  }

  @Nullable
  protected PsiElement performRefactoring(DartIntroduceOperation operation) {
    PsiElement declaration = createDeclaration(operation);
    if (declaration == null) {
      showCannotPerformError(operation.getProject(), operation.getEditor());
      return null;
    }

    declaration = performReplace(declaration, operation);
    declaration = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(declaration);
    return declaration;
  }

  @Nullable
  public PsiElement createDeclaration(DartIntroduceOperation operation) {
    final Project project = operation.getProject();
    final DartExpression initializer = operation.getInitializer();
    InitializerTextBuilder builder = new InitializerTextBuilder();
    initializer.accept(builder);
    String assignmentText = getDeclarationString(operation, builder.result());
    PsiElement anchor = operation.isReplaceAll()
                        ? findAnchor(operation.getOccurrences())
                        : findAnchor(initializer);
    return createDeclaration(project, assignmentText, anchor);
  }

  abstract protected String getDeclarationString(DartIntroduceOperation operation, String initExpression);

  @Nullable
  protected PsiElement createDeclaration(Project project, String text, PsiElement anchor) {
    return DartElementGenerator.createStatementFromText(project, text);
  }

  private PsiElement performReplace(@NotNull final PsiElement declaration, final DartIntroduceOperation operation) {
    final DartExpression expression = operation.getInitializer();
    final Project project = operation.getProject();
    return new WriteCommandAction<PsiElement>(project, expression.getContainingFile()) {
      protected void run(final Result<PsiElement> result) throws Throwable {
        final PsiElement createdDeclaration = addDeclaration(operation, declaration);
        result.setResult(createdDeclaration);
        if (createdDeclaration != null) {
          modifyDeclaration(createdDeclaration);
        }

        PsiElement newExpression = createExpression(project, operation.getName());

        if (operation.isReplaceAll()) {
          List<PsiElement> newOccurrences = new ArrayList<PsiElement>();
          for (PsiElement occurrence : operation.getOccurrences()) {
            final PsiElement replaced = replaceExpression(occurrence, newExpression, operation);
            if (replaced != null) {
              newOccurrences.add(replaced);
            }
          }
          operation.setOccurrences(newOccurrences);
        }
        else {
          final PsiElement replaced = replaceExpression(expression, newExpression, operation);
          operation.setOccurrences(Collections.singletonList(replaced));
        }

        postRefactoring(operation.getElement());
      }
    }.execute().getResultObject();
  }

  protected void modifyDeclaration(@NotNull PsiElement declaration) {
    final PsiElement parent = declaration.getParent();

    PsiElement newLineNode = PsiParserFacade.SERVICE.getInstance(declaration.getProject()).createWhiteSpaceFromText("\n");
    parent.addAfter(newLineNode, declaration);

    final ASTNode nextChild = declaration.getNode().getTreeNext();
    parent.getNode().addLeaf(DartTokenTypes.SEMICOLON, ";", nextChild);
  }

  @Nullable
  protected DartReference createExpression(Project project, String name) {
    return DartElementGenerator.createReferenceFromText(project, name);
  }

  @Nullable
  protected PsiElement replaceExpression(PsiElement expression, PsiElement newExpression, DartIntroduceOperation operation) {
    return expression.replace(newExpression);
  }


  protected void postRefactoring(PsiElement element) {
  }

  @Nullable
  public PsiElement addDeclaration(DartIntroduceOperation operation, PsiElement declaration) {
    PsiElement anchor = operation.isReplaceAll() ? findAnchor(operation.getOccurrences()) : findAnchor(operation.getInitializer());
    assert anchor != null;
    final PsiElement parent = anchor.getParent();
    return parent.addBefore(declaration, anchor);
  }


  private static class DartInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final DartComponentName myTarget;

    public DartInplaceVariableIntroducer(DartComponentName target,
                                         DartIntroduceOperation operation,
                                         List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), "Introduce Variable",
            occurrences.toArray(new PsiElement[occurrences.size()]), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }

  private static class InitializerTextBuilder extends PsiRecursiveElementVisitor {
    private final StringBuilder myResult = new StringBuilder();

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
      myResult.append(space.getText().replace('\n', ' '));
    }

    @Override
    public void visitElement(PsiElement element) {
      if (element.getChildren().length == 0) {
        myResult.append(element.getText());
      }
      else {
        super.visitElement(element);
      }
    }

    public String result() {
      return myResult.toString();
    }
  }
}
