package br.com.alura.estoque.repository;

import android.os.AsyncTask;

import java.io.IOException;
import java.util.List;

import br.com.alura.estoque.asynctask.BaseAsyncTask;
import br.com.alura.estoque.database.dao.ProdutoDAO;
import br.com.alura.estoque.model.Produto;
import br.com.alura.estoque.retrofit.EstoqueRetrofit;
import br.com.alura.estoque.retrofit.service.ProdutoService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.internal.EverythingIsNonNull;

public class ProdutoRepository {

    private final ProdutoDAO dao;
    private ProdutoService service;

    public ProdutoRepository(ProdutoDAO dao) {
        this.dao = dao;
    }

    public void buscaProdutos(DadosCarregadosListener<List<Produto>> listener) {
        buscaProdutosInternos(listener);
        service = new EstoqueRetrofit().getProdutoService();
    }

    private void buscaProdutosInternos(DadosCarregadosListener<List<Produto>> listener) {
        new BaseAsyncTask<>(dao::buscaTodos,
                resultado -> {
                    // notifica que o dado esta pronto
                    listener.quandoCarregados(resultado);
                    buscaProdutosNaApi(listener);
                }).execute();
    }

    private void buscaProdutosNaApi(DadosCarregadosListener<List<Produto>> listener) {
        Call<List<Produto>> call = service.buscaTodos();
        new BaseAsyncTask<>(() -> {
            try {
                Response<List<Produto>> resposta = call.execute();
                List<Produto> produtosNovos = resposta.body();
                dao.salva(produtosNovos);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return dao.buscaTodos();
        }, produtosNovos -> {
            // notifica que o dado esta pronto
            listener.quandoCarregados(produtosNovos);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void salva(Produto produto, DadosCarregadosCallback<Produto> callback) {
        salvaApi(produto, callback);
    }

    private void salvaApi(Produto produto, DadosCarregadosCallback<Produto> callback) {
        Call<Produto> call = service.salva(produto);
        call.enqueue(new Callback<Produto>() {

            @Override
            @EverythingIsNonNull
            public void onResponse(Call<Produto> call,
                                   Response<Produto> response) {
                if (response.isSuccessful()) {
                    Produto produtoSalvo = response.body();
                    if (produtoSalvo != null)
                        salvaInterno(produtoSalvo, callback);
                } else {
                    //notificar uma falha
                    callback.quandoFalha("Mensagem não sucedida.");
                }
            }

            @Override
            @EverythingIsNonNull
            public void onFailure(Call<Produto> call, Throwable t) {
                //notificar uma falha
                callback.quandoFalha("Falha de comunicação: " + t.getMessage());
            }
        });
    }

    private void salvaInterno(Produto produtoSalvo, DadosCarregadosCallback<Produto> callback) {
        new BaseAsyncTask<>(() -> {
            long id = dao.salva(produtoSalvo);
            return dao.buscaProduto(id);
        }, callback::quandoSucesso)
                .execute();
    }

    public interface DadosCarregadosListener<T> {
        void quandoCarregados(T resultado);
    }

    public interface DadosCarregadosCallback<T> {
        void quandoSucesso(T resultado);
        void quandoFalha(String erro);
    }
}
