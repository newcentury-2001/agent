"use client";

import { MessageSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ResponsiveDialog } from "@/components/layout/ResponsiveDialog";
import { SplitLayout } from "@/components/layout/SplitLayout";
import { PlanSidebar } from "@/components/plan-sidebar";
import { ChatMessageList } from "./ChatMessageList";
import { ChatInputArea } from "./ChatInputArea";
import { FileDetailPanel } from "./FileDetailPanel";
import { useRagChatSession } from "@/hooks/rag-chat/useRagChatSession";
import { useChatLayout } from "@/hooks/rag-chat/useChatLayout";
import { toast } from "@/hooks/use-toast";
import type { RagDataset, RetrievedFileInfo, DocumentSegment } from "@/types/rag-dataset";
import type { PlanStep } from "@/types/plan";

interface RagChatDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  dataset: RagDataset;
}

export function RagChatDialog({ open, onOpenChange, dataset }: RagChatDialogProps) {
  const placeholderSteps: PlanStep[] = [
    { stepNo: 1, title: "文档检索", status: "TODO" },
    { stepNo: 2, title: "生成回答", status: "TODO" }
  ];
  const {
    uiState,
    selectFile,
    selectSegment,
    closeFileDetail,
    setFileDetailData,
    resetState
  } = useChatLayout();

  const {
    messages,
    isLoading,
    sendMessage,
    clearMessages,
    stopGeneration,
    sessionId
  } = useRagChatSession({
    onError: (error) => {
      toast({
        title: "对话出错",
        description: error,
        variant: "destructive"
      });
    }
  });

  // 处理文件点击
  const handleFileClick = (file: RetrievedFileInfo) => {
    selectFile(file);
  };

  // 处理文档片段点击
  const handleSegmentClick = (segment: DocumentSegment) => {
    selectSegment(segment);
  };

  // 处理文件详情数据加载
  const handleFileDetailDataLoad = (data: any) => {
    setFileDetailData(data);
  };

  // 处理发送消息
  const handleSendMessage = async (message: string) => {
    await sendMessage(message, [dataset.id]);
  };

  // 处理对话框关闭
  const handleDialogClose = (open: boolean) => {
    if (!open) {
      resetState();
    }
    onOpenChange(open);
  };

  // 处理清空对话
  const handleClearMessages = () => {
    clearMessages();
    closeFileDetail();
  };

  return (
    <ResponsiveDialog
      open={open}
      onOpenChange={handleDialogClose}
      title={
        <div className="flex items-center gap-3">
          <MessageSquare className="h-5 w-5" />
          <span>RAG 智能问答</span>
          <Badge variant="secondary">{dataset.name}</Badge>
        </div>
      }
      layout={uiState.layout}
    >
      <SplitLayout
        leftPanel={
          <div className="flex h-full relative">
            <div className="flex-1 flex flex-col">
              <ChatMessageList
                messages={messages}
                onFileClick={handleFileClick}
                onSegmentClick={handleSegmentClick}
                selectedFileId={uiState.selectedFile?.fileId}
                selectedSegmentId={uiState.selectedSegment?.documentId}
                className="flex-1"
              />
              
              <ChatInputArea
                onSend={handleSendMessage}
                onStop={stopGeneration}
                onClear={handleClearMessages}
                isLoading={isLoading}
                hasMessages={messages.length > 0}
              />
            </div>
            <div className="hidden xl:flex">
              <PlanSidebar conversationId={sessionId || undefined} placeholderSteps={placeholderSteps} position="absolute" />
            </div>
          </div>
        }
        rightPanel={
          <FileDetailPanel
            selectedFile={uiState.selectedFile}
            selectedSegment={uiState.selectedSegment}
            onDataLoad={handleFileDetailDataLoad}
          />
        }
        showRightPanel={uiState.showFileDetail}
        onCloseRightPanel={closeFileDetail}
      />
    </ResponsiveDialog>
  );
}
