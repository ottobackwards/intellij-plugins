package com.jetbrains.lang.dart.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.jetbrains.lang.dart.DartTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: Fedor.Korotkov
 */
public class UsefulPsiTreeUtil {
  public static ASTNode[] findChildrenRange(ASTNode[] elements, int startOffset, int endOffset) {
    int i = findChildIndex(elements, startOffset);
    int j = findChildIndex(elements, endOffset);
    i = i == -1 ? 0 : i;
    j = j == -1 ? elements.length : j;
    // trim
    while (0 < j && j < elements.length && elements[j].getElementType() == TokenType.WHITE_SPACE) {
      --j;
    }
    int to = j;
    if (j < elements.length && elements[j].getElementType() != DartTokenTypes.SEMICOLON) {
      // try eat until ';'
      while (j + 1 < elements.length && (elements[j + 1].getElementType() == DartTokenTypes.SEMICOLON ||
                                         elements[j + 1].getElementType() == TokenType.WHITE_SPACE)) {
        ++j;
        if (elements[j].getElementType() == DartTokenTypes.SEMICOLON) {
          to = j;
        }
      }
    }
    to = Math.min(elements.length, to + 1);
    if (to < i) {
      return ASTNode.EMPTY_ARRAY;
    }
    return Arrays.copyOfRange(elements, i, to);
  }

  private static int findChildIndex(ASTNode[] children, int offset) {
    for (int i = 0, length = children.length; i < length; i++) {
      ASTNode child = children[i];
      if (child.getTextRange().contains(offset)) {
        return i;
      }
    }

    return -1;
  }

  public static boolean isWhitespaceOrComment(PsiElement element) {
    return element instanceof PsiWhiteSpace || element instanceof PsiComment;
  }

  @Nullable
  public static List<PsiElement> getPathToParentOfType(@Nullable PsiElement element,
                                                       @NotNull Class<? extends PsiElement> aClass) {
    if (element == null) return null;
    final List<PsiElement> result = new ArrayList<PsiElement>();
    while (element != null) {
      result.add(element);
      if (aClass.isInstance(element)) {
        return result;
      }
      if (element instanceof PsiFile) return null;
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  public static PsiElement getNextSiblingSkippingWhiteSpacesAndComments(PsiElement sibling) {
    return getSiblingSkippingCondition(
      sibling,
      new Function<PsiElement, PsiElement>() {
        @Nullable
        @Override
        public PsiElement fun(PsiElement element) {
          return element.getNextSibling();
        }
      }, new Condition<PsiElement>() {
        @Override
        public boolean value(PsiElement element) {
          return isWhitespaceOrComment(element);
        }
      }, true
    );
  }

  @Nullable
  public static PsiElement getPrevSiblingSkipWhiteSpacesAndComments(@Nullable PsiElement sibling, boolean strictly) {
    return getPrevSiblingSkippingCondition(sibling, new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return isWhitespaceOrComment(element);
      }
    }, strictly);
  }

  @Nullable
  public static ASTNode getPrevSiblingSkipWhiteSpacesAndComments(@Nullable ASTNode sibling) {
    if (sibling == null) return null;
    ASTNode result = sibling.getTreePrev();
    while (result != null && isWhitespaceOrComment(result.getPsi())) {
      result = result.getTreePrev();
    }
    return result;
  }

  @Nullable
  public static PsiElement getPrevSiblingSkipWhiteSpaces(@Nullable PsiElement sibling, boolean strictly) {
    return getPrevSiblingSkippingCondition(sibling, new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof PsiWhiteSpace;
      }
    }, strictly);
  }

  @Nullable
  public static PsiElement getPrevSiblingSkippingCondition(@Nullable PsiElement sibling,
                                                           Condition<PsiElement> condition,
                                                           boolean strictly) {
    return getSiblingSkippingCondition(sibling, new Function<PsiElement, PsiElement>() {
      @Nullable
      @Override
      public PsiElement fun(PsiElement element) {
        return element.getPrevSibling();
      }
    }, condition, strictly);
  }

  @Nullable
  public static PsiElement getSiblingSkippingCondition(@Nullable PsiElement sibling,
                                                       Function<PsiElement, PsiElement> nextSibling,
                                                       Condition<PsiElement> condition,
                                                       boolean strictly) {
    if (sibling == null) return null;
    PsiElement result = strictly ? nextSibling.fun(sibling) : sibling;
    while (result != null && condition.value(result)) {
      result = nextSibling.fun(result);
    }
    return result;
  }

  @Nullable
  public static <T extends PsiElement> T[] getChildrenOfType(@Nullable PsiElement element,
                                                             @NotNull Class<T> aClass,
                                                             @Nullable PsiElement lastParent) {
    if (element == null) return null;

    List<T> result = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (lastParent == child) {
        break;
      }
      if (aClass.isInstance(child)) {
        if (result == null) result = new SmartList<T>();
        //noinspection unchecked
        result.add((T)child);
      }
    }
    return result == null ? null : ArrayUtil.toObjectArray(result, aClass);
  }
}
