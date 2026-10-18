import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { Todo } from "./client";
import {
  createTodoMutation,
  deleteTodoMutation,
  listTodosQueryKey,
  toggleTodoMutation,
  updateTodoMutation,
} from "./generated/@tanstack/react-query.gen";

// Sin argumentos, la key de `listTodos` es prefijo de todas sus variantes de filtro:
// TanStack Query compara las keys por coincidencia parcial.
const allTodosQueryKey = listTodosQueryKey();

function useInvalidateTodos() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: allTodosQueryKey });
}

export function useCreateTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...createTodoMutation(), onSettled: invalidate });
}

export function useUpdateTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...updateTodoMutation(), onSettled: invalidate });
}

export function useDeleteTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...deleteTodoMutation(), onSettled: invalidate });
}

/**
 * Toggle con update optimista: el checkbox cambia al instante y, si el servidor falla,
 * se restauran las listas que había en caché. Al terminar se refetchea para quedar sincronizados
 * (y para que el TODO salga de la lista si estamos filtrando por activos/completados).
 */
export function useToggleTodo() {
  const queryClient = useQueryClient();
  return useMutation({
    ...toggleTodoMutation(),
    onMutate: async ({ path }) => {
      await queryClient.cancelQueries({ queryKey: allTodosQueryKey });
      const previous = queryClient.getQueriesData<Todo[]>({ queryKey: allTodosQueryKey });
      queryClient.setQueriesData<Todo[]>({ queryKey: allTodosQueryKey }, (todos) =>
        todos?.map((todo) =>
          todo.id === path.id ? { ...todo, completed: !todo.completed } : todo,
        ),
      );
      return { previous };
    },
    onError: (_error, _variables, context) => {
      for (const [queryKey, data] of context?.previous ?? []) {
        queryClient.setQueryData(queryKey, data);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: allTodosQueryKey }),
  });
}
