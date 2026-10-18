import { useQueryClient } from "@tanstack/react-query";
import { $api, type Todo, todosQueryKey } from "./client";

function useInvalidateTodos() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: todosQueryKey });
}

export function useCreateTodo() {
  const invalidate = useInvalidateTodos();
  return $api.useMutation("post", "/api/todos", { onSettled: invalidate });
}

export function useUpdateTodo() {
  const invalidate = useInvalidateTodos();
  return $api.useMutation("patch", "/api/todos/{id}", { onSettled: invalidate });
}

export function useDeleteTodo() {
  const invalidate = useInvalidateTodos();
  return $api.useMutation("delete", "/api/todos/{id}", { onSettled: invalidate });
}

/**
 * Toggle con update optimista: el checkbox cambia al instante y, si el servidor falla,
 * se restauran las listas que había en caché. Al terminar se refetchea para quedar sincronizados
 * (y para que el TODO salga de la lista si estamos filtrando por activos/completados).
 */
export function useToggleTodo() {
  const queryClient = useQueryClient();
  return $api.useMutation("post", "/api/todos/{id}/toggle", {
    onMutate: async ({ params }) => {
      await queryClient.cancelQueries({ queryKey: todosQueryKey });
      const previous = queryClient.getQueriesData<Todo[]>({ queryKey: todosQueryKey });
      queryClient.setQueriesData<Todo[]>({ queryKey: todosQueryKey }, (todos) =>
        todos?.map((todo) =>
          todo.id === params.path.id ? { ...todo, completed: !todo.completed } : todo,
        ),
      );
      return { previous };
    },
    onError: (_error, _variables, context) => {
      for (const [queryKey, data] of context?.previous ?? []) {
        queryClient.setQueryData(queryKey, data);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: todosQueryKey }),
  });
}
