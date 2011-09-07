/*
 * Copyright 2011 The authors
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

package com.intellij.lang.ognl.template;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.Language;
import com.intellij.lang.ognl.OgnlFileType;
import com.intellij.lang.ognl.OgnlLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Provides live template context for OGNL.
 *
 * @author Yann C&eacute;bron
 */
public class OgnlTemplateContextType extends TemplateContextType {

  public OgnlTemplateContextType() {
    super(OgnlLanguage.ID, OgnlLanguage.ID);
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile psiFile, final int offset) {
    if (isInContext(psiFile.getFileType())) {
      return true;
    }

    final PsiElement at = psiFile.findElementAt(offset);
    final Language language = at != null ? at.getParent().getLanguage() : null;
    final boolean b = language != null && language == OgnlLanguage.INSTANCE;
    return b;
  }

  @Override
  public boolean isInContext(@NotNull final FileType fileType) {
    return fileType == OgnlFileType.INSTANCE;
  }

}