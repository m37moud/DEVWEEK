package dev.daymark.models

sealed interface DeletionResult {
    data object Deleted : DeletionResult;
    data object Failed : DeletionResult
}
