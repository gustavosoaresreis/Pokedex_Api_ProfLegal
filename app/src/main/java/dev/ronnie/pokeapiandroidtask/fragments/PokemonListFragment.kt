package dev.ronnie.pokeapiandroidtask.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.ronnie.pokeapiandroidtask.R
import dev.ronnie.pokeapiandroidtask.adapters.LoadingStateAdapter
import dev.ronnie.pokeapiandroidtask.adapters.PokemonAdapter
import dev.ronnie.pokeapiandroidtask.databinding.FragmentPokemonListBinding
import dev.ronnie.pokeapiandroidtask.model.PokemonResult
import dev.ronnie.pokeapiandroidtask.utils.PRODUCT_VIEW_TYPE
import dev.ronnie.pokeapiandroidtask.utils.toast
import dev.ronnie.pokeapiandroidtask.viewmodels.PokemonListViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *created by Ronnie Otieno on 20-Dec-20.
 **/

@AndroidEntryPoint
class PokemonListFragment : Fragment(R.layout.fragment_pokemon_list) {

    private var hasInitiatedInitialCall = false
    private lateinit var binding: FragmentPokemonListBinding
    private val viewModel: PokemonListViewModel by viewModels()
    private var job: Job? = null

    //adapter with higher order function passed which is called on onclick on adapter
    private val adapter = PokemonAdapter { pokemonResult: PokemonResult, dominantColor: Int ->
        navigate(
            pokemonResult,
            dominantColor
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentPokemonListBinding.bind(view)

        setAdapter()

        binding.searchView.setOnTouchListener { v, _ ->
            v.isFocusableInTouchMode = true
            false
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            startFetchingPokemon(null, true)

            binding.searchView.apply {
                text = null
                isFocusable = false

            }
            hideSoftKeyboard()

        }

        binding.searchView.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchView.text.toString().trim())
                return@OnEditorActionListener true
            }
            false
        })


    }

    private fun startFetchingPokemon(searchString: String?, shouldSubmitEmpty: Boolean) {

        //collecting flow then setting to adapter
        job?.cancel()
        job = lifecycleScope.launch {
            if (shouldSubmitEmpty) adapter.submitData(PagingData.empty())
            viewModel.getAdverts(searchString).collectLatest {
                adapter.submitData(it)
            }
        }
    }


    private fun performSearch(searchString: String) {
        hideSoftKeyboard()

        if (searchString.isEmpty()) {
            requireContext().toast("Search cannot be empty")
            return
        }
        startFetchingPokemon(searchString, true)


    }

    private fun hideSoftKeyboard() {
        val view = requireActivity().currentFocus

        view?.let {
            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }


    }

    private fun setAdapter() {

        val gridLayoutManager = GridLayoutManager(requireContext(), 2)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = adapter.getItemViewType(position)
                return if (viewType == PRODUCT_VIEW_TYPE) 1
                else 2
            }
        }
        binding.pokemonList.layoutManager = gridLayoutManager
        binding.pokemonList.adapter = adapter.withLoadStateFooter(
            footer = LoadingStateAdapter { retry() }
        )

        if (!hasInitiatedInitialCall) startFetchingPokemon(null, false); hasInitiatedInitialCall =
            true

        //the progress will only show when the adapter is refreshing and its empty
        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Loading && adapter.snapshot().isEmpty()
            ) {
                binding.progressCircular.isVisible = true
                binding.textError.isVisible = false


            } else {
                binding.progressCircular.isVisible = false
                binding.swipeRefreshLayout.isRefreshing = false

                //if there is error a textview will show the error encountered.

                val error = when {
                    loadState.prepend is LoadState.Error -> loadState.prepend as LoadState.Error
                    loadState.append is LoadState.Error -> loadState.append as LoadState.Error
                    loadState.refresh is LoadState.Error -> loadState.refresh as LoadState.Error

                    else -> null
                }
                if (adapter.snapshot().isEmpty()) {
                    error?.let {
                        binding.textError.visibility = View.VISIBLE
                        binding.textError.setOnClickListener {
                            adapter.retry()
                        }
                    }

                }
            }
        }

    }

    override fun onPause() {
        super.onPause()
        binding.searchView.isFocusable = false
    }

    override fun onResume() {
        super.onResume()
//setting the status bar color back
        requireActivity().window.statusBarColor =
            ContextCompat.getColor(requireContext(), R.color.green)
    }

    private fun retry() {
        adapter.retry()
    }

    //navigating to stats fragment passing the pokemon and the dominant color
    private fun navigate(pokemonResult: PokemonResult, dominantColor: Int) {
        binding.root.findNavController()
            .navigate(
                PokemonListFragmentDirections.toPokemonStatsFragment(
                    pokemonResult,
                    dominantColor
                )
            )
    }

}