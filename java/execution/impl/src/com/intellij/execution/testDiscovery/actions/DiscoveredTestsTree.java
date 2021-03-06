// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.psi.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.FontUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class DiscoveredTestsTree extends Tree implements DataProvider {
  private final DiscoveredTestsTreeModel myModel;

  public DiscoveredTestsTree(String title) {
    myModel = new DiscoveredTestsTreeModel();
    setModel(new AsyncTreeModel(myModel));
    HintUpdateSupply.installSimpleHintUpdateSupply(this);
    TreeUIHelper.getInstance().installTreeSpeedSearch(this, o -> {
      Object component = o.getLastPathComponent();
      return component instanceof PsiMember ? ((PsiMember)component).getName() : null;
    }, true);
    getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
    getEmptyText().setText("No tests captured for " + title);
    setPaintBusy(true);
    setRootVisible(false);
    setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof DiscoveredTestsTreeModel.Node) {
          DiscoveredTestsTreeModel.Node node = (DiscoveredTestsTreeModel.Node)value;
          setIcon(node.getIcon());
          String name = node.getName();
          assert name != null;
          append(name);
          if (node.isClass()) {
            String packageName = node.getPackageName();
            if (packageName != null) {
              append(FontUtil.spaceAndThinSpace() + packageName, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            int testMethodCount = myModel.getChildren(value).size();
            append(" / " + (testMethodCount != 1 ? (testMethodCount + " tests") : "1 test"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, false);
        }
      }
    });
    getModel().addTreeModelListener(new TreeModelAdapter() {
      //TODO
      boolean myAlreadyDone;
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        if (!myAlreadyDone && !myModel.getTestClasses().isEmpty()) {
          myAlreadyDone = true;
          EdtInvocationManager.getInstance().invokeLater(() -> TreeUtil.selectFirstNode(DiscoveredTestsTree.this));
        }
      }
    });
  }

  public synchronized void addTest(@NotNull PsiClass testClass,
                                   @NotNull PsiMethod testMethod) {
    myModel.addTest(testClass, testMethod);
  }

  @NotNull
  public Set<Module> getContainingModules() {
    return myModel.getTestClasses().stream()
                  .map(element -> {
                    SmartPsiElementPointer<PsiClass> pointer = element.getPointer();
                    return ModuleUtilCore.findModuleForFile(pointer.getVirtualFile(), pointer.getProject());
                  })
                  .filter(module -> module != null)
                  .collect(Collectors.toSet());
  }

  public DiscoveredTestsTreeModel.Node<PsiMethod>[] getTestMethods() {
    return myModel.getTestMethods();
  }

  public PsiElement getSelectedElement() {
    TreePath path = getSelectionModel().getSelectionPath();
    return ObjectUtils.tryCast(path == null ? null : path.getLastPathComponent(), PsiElement.class);
  }

  public int getTestCount() {
    return myModel.getTestCount();
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      TreePath[] paths = getSelectionModel().getSelectionPaths();
      List<PsiElement> result = ContainerUtil.newSmartList();
      TreeModel model = getModel();
      for (TreePath p : paths) {
        Object e = p.getLastPathComponent();
        if (e instanceof PsiMethod) {
          result.add((PsiMethod)e);
        }
        else {
          int count = model.getChildCount(e);
          if (count == 0 && e instanceof PsiElement) {
            result.add((PsiElement)e);
          }
          else {
            for (int i = 0; i < count; i++) {
              ContainerUtil.addIfNotNull(result, ObjectUtils.tryCast(model.getChild(e, i), PsiMethod.class));
            }
          }
        }
      }
      return result.toArray(PsiElement.EMPTY_ARRAY);
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      return getSelectedElement();
    }
    else if (LangDataKeys.POSITION_ADJUSTER_POPUP.is(dataId)) {
      return PopupUtil.getPopupContainerFor(this);
    }
    return null;
  }
}
