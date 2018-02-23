import React, {Component} from 'react';
import config from "../config";
import Offer from "./Offer";
import Error from "./Error";


class Home extends Component {

  source;

  constructor() {
    super();

    this.state = {
      offer: null,
      error: null
    }
  }

  componentDidMount() {
    this.loadOffers();
  }

  componentWillUnmount() {
    this.source.close();
  }


  loadOffers() {
    this.source = new EventSource(`${config.SERVER_URL}/offers`);
    this.source.onmessage = (e) => {
      const offer = JSON.parse(e.data);
      this.setState({offer, error: null})
    };
    this.source.onerror = () => {
      this.setState({error: 'Could not load offers', offer: null})
    }
  }

  render() {
    const {offer, error} = this.state;

    return <div>
      <Offer offer={offer}/>
      <Error message={error} />

    </div>
  }
}

export default Home;