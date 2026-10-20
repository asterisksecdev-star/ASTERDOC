package com.asterdoc.app.ui.tools;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asterdoc.app.R;
import com.asterdoc.app.ui.tools.arranger.PdfArrangerActivity;
import com.asterdoc.app.ui.tools.sign.SignPdfActivity;

import java.util.ArrayList;
import java.util.List;

public class ToolsFragment extends Fragment implements ToolAdapter.OnToolClickListener {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tools, container, false);

        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_down);

        RecyclerView rvScan = view.findViewById(R.id.rvScanTools);
        rvScan.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvScan.setAdapter(new ToolAdapter(getScanTools(), this));
        rvScan.setLayoutAnimation(animation);

        RecyclerView rvConvert = view.findViewById(R.id.rvConvertTools);
        rvConvert.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvConvert.setAdapter(new ToolAdapter(getConvertTools(), this));
        rvConvert.setLayoutAnimation(animation);

        RecyclerView rvEdit = view.findViewById(R.id.rvEditTools);
        rvEdit.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvEdit.setAdapter(new ToolAdapter(getEditTools(), this));
        rvEdit.setLayoutAnimation(animation);

        return view;
    }

    private List<ToolItem> getScanTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("s1", getString(R.string.scan_document), android.R.drawable.ic_menu_camera, "SCAN"));
        list.add(new ToolItem("s2", getString(R.string.ocr_text_extract), android.R.drawable.ic_menu_search, "OCR"));
        return list;
    }

    private List<ToolItem> getConvertTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("c1", getString(R.string.pdf_to_word), android.R.drawable.ic_menu_edit, "PDF_WORD"));
        list.add(new ToolItem("c2", getString(R.string.word_to_pdf), android.R.drawable.ic_menu_save, "WORD_PDF"));
        list.add(new ToolItem("c3", getString(R.string.excel_to_pdf), android.R.drawable.ic_menu_sort_by_size, "EXCEL_PDF"));
        list.add(new ToolItem("c4", getString(R.string.pdf_to_excel), android.R.drawable.ic_menu_manage, "PDF_EXCEL"));
        list.add(new ToolItem("c5", getString(R.string.image_to_pdf), android.R.drawable.ic_menu_gallery, "IMAGES_PDF"));
        return list;
    }

    private List<ToolItem> getEditTools() {
        List<ToolItem> list = new ArrayList<>();
        list.add(new ToolItem("e1", getString(R.string.merge_pdf), android.R.drawable.ic_menu_add, "MERGE"));
        list.add(new ToolItem("e2", getString(R.string.split_pdf), android.R.drawable.ic_menu_crop, "SPLIT"));
        list.add(new ToolItem("e3", getString(R.string.compress_pdf), android.R.drawable.ic_menu_sort_by_size, "COMPRESS"));
        list.add(new ToolItem("e4", getString(R.string.sign_pdf), android.R.drawable.ic_menu_send, "SIGN"));
        list.add(new ToolItem("e5", getString(R.string.page_arranger), android.R.drawable.ic_menu_sort_alphabetically, "ARRANGE"));
        return list;
    }

    @Override
    public void onToolClick(ToolItem tool) {
        String type = tool.getToolType();

        Intent intent;
        if ("ARRANGE".equals(type)) {
            intent = new Intent(requireContext(), PdfArrangerActivity.class);
        } else if ("SIGN".equals(type)) {
            intent = new Intent(requireContext(), SignPdfActivity.class);
        } else {
            intent = new Intent(requireContext(), ToolActionActivity.class);
            intent.putExtra("TOOL_TYPE", type);
            intent.putExtra("TOOL_TITLE", tool.getTitle());
        }
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_bottom, R.anim.fade_out);
        }
    }
}
