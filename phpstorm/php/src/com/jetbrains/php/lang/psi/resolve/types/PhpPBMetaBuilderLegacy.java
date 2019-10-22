package com.jetbrains.php.lang.psi.resolve.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpRecursiveElementVisitor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public class PhpPBMetaBuilderLegacy {

  public static final String TYPE_PROVIDER_VARIABLE_NAME = "STATIC_METHOD_TYPES";

  @NotNull
  static Map<String, Map<String, String>> getMap(Collection<PsiFile> files) {
    Map<String, Map<String, String>> map = new THashMap<>();
    Collection<Variable> variables = getVariables(files, TYPE_PROVIDER_VARIABLE_NAME);
    for (Variable variable : variables) {
      if (!"\\PHPSTORM_META\\".equals(variable.getNamespaceName())) continue;
      PsiElement parent = variable.getParent();
      if (parent instanceof AssignmentExpression) {
        PhpPsiElement value = ((AssignmentExpression)parent).getValue();
        if (value instanceof ArrayCreationExpression) {
          //[ ? => ? ,...]
          for (ArrayHashElement element : ((ArrayCreationExpression)value).getHashElements()) {
            PhpPsiElement match = element.getKey();
            String matchSignature = null;
            //Class::method('')
            if (match instanceof FunctionReference) {
              matchSignature = ((FunctionReference)match).getSignature();
            }
            //new Class
            else if (match instanceof NewExpression) {
              ClassReference reference = ((NewExpression)match).getClassReference();
              matchSignature = reference != null ? reference.getSignature() : null;
            }
            Map<String, String> types = map.get(matchSignature);
            if (matchSignature != null && types == null) {
              types = new THashMap<>();
              map.put(matchSignature, types);
            }
            PhpPsiElement val = element.getValue();
            collectParameterValueMapping(types, val);
          }
        }
      }
    }
    return map;
  }

  /**
   * => ['param' instanceof Class,... ]
   */
  private static void collectParameterValueMapping(Map<String, String> types, PhpPsiElement val) {
    if (val instanceof ArrayCreationExpression) {
      PhpPsiElement child = val.getFirstPsiChild();
      while (child != null) {
        if (child.getFirstPsiChild() instanceof BinaryExpression) {
          BinaryExpression binary = ((BinaryExpression)child.getFirstPsiChild());
          if (binary.getOperationType() == PhpTokenTypes.kwINSTANCEOF) {
            PsiElement leftOperand = binary.getLeftOperand();
            PsiElement rightOperand = binary.getRightOperand();
            if (leftOperand instanceof StringLiteralExpression && rightOperand != null) {
              types.put(((StringLiteralExpression)leftOperand).getContents().replace(PhpParameterBasedTypeProvider.SEPARATOR, PhpParameterBasedTypeProvider.ESCAPED_SEPARATOR), rightOperand.getText());
            }
            else if (leftOperand instanceof ConstantReference && rightOperand != null) {
              types.put((((ConstantReference)leftOperand).getSignature()).replace(PhpParameterBasedTypeProvider.SEPARATOR, PhpParameterBasedTypeProvider.ESCAPED_SEPARATOR), rightOperand.getText());
            }
            else if (leftOperand instanceof ClassConstantReference && rightOperand != null) {
              types.put((((ClassConstantReference)leftOperand).getSignature()).replace(PhpParameterBasedTypeProvider.SEPARATOR, PhpParameterBasedTypeProvider.ESCAPED_SEPARATOR), rightOperand.getText());
            }
          }
          else if (binary.getOperationType() == PhpTokenTypes.opEQUAL) {
            PsiElement leftOperand = binary.getLeftOperand();
            PsiElement rightOperand = binary.getRightOperand();
            if (leftOperand != null && rightOperand instanceof StringLiteralExpression) {
              types.put(PhpParameterBasedTypeProvider.PATTERN_KEY, ((StringLiteralExpression)rightOperand).getContents());
            }
          }
        }
        child = child.getNextPsiSibling();
      }
    }
  }

  private static Collection<Variable> getVariables(Collection<PsiFile> files, final String key) {
    final Collection<Variable> result = new SmartList<>();
    for (PsiFile file : files) {
      if (file instanceof PhpFile) {
        //AG not the most elegant way - but still an allowed usage.
        //noinspection deprecation
        file.accept(new PhpRecursiveElementVisitor() {
          @Override
          public void visitPhpAssignmentExpression(AssignmentExpression assignmentExpression) {
            PhpPsiElement variable = assignmentExpression.getVariable();
            if (variable instanceof Variable) {
              if (key.contentEquals(((Variable)variable).getNameCS())) {
                result.add((Variable)variable);
              }
            }
          }
        });
      }
    }
    return result;
  }
}